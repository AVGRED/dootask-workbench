<?php

namespace App\Module;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectPermission;
use App\Models\ProjectTask;
use App\Models\ProjectTaskDependency;
use App\Models\ProjectTaskScheduleBatch;
use App\Models\ProjectTaskUser;
use App\Models\User;
use Carbon\Carbon;
use Illuminate\Support\Facades\DB;

class ProjectTaskScheduleService
{
    private const MAX_TASKS = 2000;
    private const MAX_EDGES = 20000;
    private const MAX_SCHEDULE_CHANGES = 10000;

    public static function settings(int $projectId): array
    {
        $project = Project::userProject($projectId);
        return self::settingsData($project);
    }

    public static function batches(int $projectId): array
    {
        $project = Project::userProject($projectId);
        self::assertManager($project);
        return ProjectTaskScheduleBatch::query()
            ->where('project_id', $projectId)
            ->orderByDesc('id')
            ->limit(20)
            ->get(['id', 'request_id', 'status', 'changes', 'created_at', 'undone_at'])
            ->map(fn (ProjectTaskScheduleBatch $batch) => [
                'id' => (int)$batch->id,
                'request_id' => $batch->request_id,
                'status' => $batch->status,
                'changed_count' => count($batch->changes ?: []),
                'created_at' => $batch->created_at?->toDateTimeString(),
                'undone_at' => $batch->undone_at?->toDateTimeString(),
            ])
            ->all();
    }

    public static function updateSettings(int $projectId, array $input): array
    {
        $userId = (int)User::userid();
        $mode = $input['mode'] ?? 'manual';
        $workdays = $input['workdays'] ?? [1, 2, 3, 4, 5];
        $hasHolidays = array_key_exists('holidays', $input);
        $holidaysInput = $hasHolidays ? $input['holidays'] : null;
        $timezone = $input['timezone'] ?? config('app.timezone', 'UTC');
        $bufferDays = filter_var($input['buffer_days'] ?? 0, FILTER_VALIDATE_INT);
        if (!in_array($mode, ['manual', 'automatic'], true)
            || !is_array($workdays)
            || !$workdays
            || count($workdays) > 7
            || $bufferDays === false
            || $bufferDays < 0
            || $bufferDays > 30
            || !is_string($timezone)
            || !in_array($timezone, timezone_identifiers_list(), true)) {
            throw new ApiException('排期规则格式无效');
        }
        $normalizedDays = array_map(function ($day) {
            $day = filter_var($day, FILTER_VALIDATE_INT);
            return $day !== false && $day >= 1 && $day <= 7 ? $day : null;
        }, $workdays);
        if (in_array(null, $normalizedDays, true)) throw new ApiException('工作日格式无效');
        $workdays = array_values(array_unique($normalizedDays));
        sort($workdays);
        if (!$workdays) {
            throw new ApiException('至少选择一个工作日');
        }
        $holidays = $hasHolidays ? self::normalizeHolidays($holidaysInput) : null;
        $changed = false;

        DB::transaction(function () use ($projectId, $userId, $mode, $workdays, $holidays, $timezone, $bufferDays, &$changed) {
            $project = Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($projectId);
            if (!$project->isOwner($userId)) {
                throw new ApiException('仅项目负责人或管理员可修改排期规则');
            }
            $current = self::settingsData($project);
            $holidays = $holidays ?? $current['holidays'];
            if ($current['mode'] === $mode && $current['workdays'] === $workdays
                && $current['holidays'] === $holidays
                && $current['timezone'] === $timezone && $current['buffer_days'] === $bufferDays) {
                return;
            }
            $next = [
                'mode' => $mode,
                'workdays' => $workdays,
                'holidays' => $holidays,
                'timezone' => $timezone,
                'buffer_days' => $bufferDays,
            ];
            $project->addLog('修改项目排期规则', [
                'before' => $current,
                'after' => $next,
                'change' => [
                    ['type' => 'value', 'data' => $current['mode'] . ' → ' . $mode],
                    ['type' => 'value', 'data' => implode(',', $current['workdays']) . ' → ' . implode(',', $workdays)],
                    ['type' => 'value', 'data' => count($current['holidays']) . ' → ' . count($holidays) . ' holidays'],
                    ['type' => 'value', 'data' => $current['timezone'] . ' → ' . $timezone],
                    ['type' => 'value', 'data' => $current['buffer_days'] . ' → ' . $bufferDays . ' workdays'],
                ],
            ], $userId);
            $project->task_schedule_mode = $mode;
            $project->task_schedule_workdays = json_encode($workdays);
            $project->task_schedule_holidays = json_encode($holidays);
            $project->task_schedule_timezone = $timezone;
            $project->task_schedule_buffer_days = $bufferDays;
            $project->task_schedule_revision = max(1, (int)$project->task_schedule_revision) + 1;
            $project->save();
            $changed = true;
        });

        if ($changed) {
            try {
                Project::userProject($projectId)->pushMsg('update');
            } catch (\Throwable $e) {
                report($e);
            }
        }

        return self::settings($projectId);
    }

    public static function preview(int $projectId, int $anchorTaskId, string $startAt, string $endAt): array
    {
        if ($projectId <= 0 || $anchorTaskId <= 0) throw new ApiException('参数错误');
        $project = Project::userProject($projectId);
        self::assertManager($project);
        if ($project->task_dependency === 'close') {
            throw new ApiException('请先开启本项目的任务依赖功能');
        }
        $settings = self::settingsData($project);
        if ($settings['mode'] !== 'automatic') {
            throw new ApiException('请先在项目设置中启用依赖预演');
        }
        $timezone = $settings['timezone'];
        try {
            $anchorStart = self::parseProjectTime($startAt, $timezone);
            $anchorEnd = self::parseProjectTime($endAt, $timezone);
        } catch (\Throwable $e) {
            throw new ApiException('排期时间格式无效');
        }
        if ($anchorEnd->lessThanOrEqualTo($anchorStart)) {
            throw new ApiException('计划结束时间必须晚于开始时间');
        }

        $tasks = ProjectTask::query()
            ->where('project_id', $projectId)
            ->whereNull('archived_at')
            ->orderBy('id')
            ->limit(self::MAX_TASKS + 1)
            ->with('taskUser')
            ->get(['id', 'parent_id', 'name', 'start_at', 'end_at', 'loop', 'loop_at', 'complete_at', 'archived_at', 'updated_at']);
        if ($tasks->count() > self::MAX_TASKS) {
            throw new ApiException('项目任务数量超出预演上限，请缩小范围后重试');
        }
        $taskMap = $tasks->keyBy(fn (ProjectTask $task) => (int)$task->id);
        $permissionTaskMap = $taskMap;
        $permissionAncestors = collect();
        $pendingAncestorIds = $tasks->pluck('parent_id')->map(fn ($id) => (int)$id)
            ->filter(fn ($id) => $id > 0 && !$permissionTaskMap->has($id))->unique()->values();
        for ($depth = 0; $depth < 5 && $pendingAncestorIds->isNotEmpty(); $depth++) {
            $ancestors = ProjectTask::query()->where('project_id', $projectId)
                ->whereIn('id', $pendingAncestorIds)->orderBy('id')->with('taskUser')
                ->get(['id', 'parent_id', 'updated_at']);
            $pendingAncestorIds = collect();
            foreach ($ancestors as $ancestor) {
                $ancestorId = (int)$ancestor->id;
                if ($permissionTaskMap->has($ancestorId)) continue;
                $permissionTaskMap->put($ancestorId, $ancestor);
                $permissionAncestors->push($ancestor);
                $parentId = (int)$ancestor->parent_id;
                if ($parentId > 0 && !$permissionTaskMap->has($parentId)) $pendingAncestorIds->push($parentId);
            }
            $pendingAncestorIds = $pendingAncestorIds->unique()->values();
        }
        $anchor = $taskMap->get($anchorTaskId);
        if (!$anchor || (int)$anchor->parent_id !== 0) {
            throw new ApiException('排期任务不存在或不是当前项目的主任务');
        }
        if ($anchor->complete_at) {
            throw new ApiException('已完成任务不能作为排期起点');
        }
        $taskTimePermissions = array_values(array_unique(array_map(
            'intval',
            ProjectPermission::getPermission($projectId, ProjectPermission::TASK_TIME)
        )));
        sort($taskTimePermissions);
        ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_TIME, $anchor);
        $anchorOldStart = self::formatProjectTime($anchor->start_at, $timezone);
        $anchorOldEnd = self::formatProjectTime($anchor->end_at, $timezone);

        $edges = ProjectTaskDependency::query()
            ->where('project_id', $projectId)
            ->orderBy('id')
            ->limit(self::MAX_EDGES + 1)
            ->get(['id', 'predecessor_task_id', 'successor_task_id']);
        if ($edges->count() > self::MAX_EDGES) {
            throw new ApiException('项目依赖数量超出预演上限');
        }

        $successors = [];
        $predecessors = [];
        foreach ($edges as $edge) {
            $from = (int)$edge->predecessor_task_id;
            $to = (int)$edge->successor_task_id;
            if (!$taskMap->has($from) || !$taskMap->has($to)
                || (int)$taskMap->get($from)->parent_id !== 0 || (int)$taskMap->get($to)->parent_id !== 0) continue;
            $successors[$from][] = $to;
            $predecessors[$to][] = $from;
        }

        $affected = [$anchorTaskId => true];
        $pending = [$anchorTaskId];
        for ($cursor = 0, $pendingCount = count($pending); $cursor < $pendingCount; $cursor++) {
            $id = $pending[$cursor];
            foreach ($successors[$id] ?? [] as $nextId) {
                if (!isset($affected[$nextId])) {
                    $affected[$nextId] = true;
                    $pending[] = $nextId;
                    $pendingCount++;
                }
            }
        }
        $indegree = array_fill_keys(array_keys($affected), 0);
        foreach (array_keys($affected) as $id) {
            foreach ($predecessors[$id] ?? [] as $from) {
                if (isset($affected[$from])) $indegree[$id]++;
            }
        }
        $queue = new \SplPriorityQueue();
        $queue->setExtractFlags(\SplPriorityQueue::EXTR_DATA);
        foreach ($indegree as $id => $degree) {
            if ($degree === 0) $queue->insert((int)$id, -(int)$id);
        }

        $effective = [];
        foreach ($taskMap as $id => $task) {
            if ($task->start_at && $task->end_at) {
                $effectiveStart = Carbon::parse($task->start_at)->setTimezone($timezone);
                $effectiveEnd = Carbon::parse($task->end_at)->setTimezone($timezone);
                if ($effectiveEnd->greaterThan($effectiveStart)) {
                    $effective[$id] = [$effectiveStart, $effectiveEnd];
                }
            }
        }
        $effective[$anchorTaskId] = [$anchorStart->copy(), $anchorEnd->copy()];
        $changes = [];
        $skipped = [];
        $expandedSubtasks = 0;
        $processed = 0;

        while (!$queue->isEmpty()) {
            $id = (int)$queue->extract();
            $processed++;
            $task = $taskMap->get($id);
            if ($id !== $anchorTaskId) {
                $reason = null;
                if ($task->complete_at) {
                    $reason = '已完成任务保持原计划';
                    if (!$task->start_at || !$task->end_at) {
                        unset($effective[$id]);
                    } else {
                        $completedStart = Carbon::parse($task->start_at)->setTimezone($timezone);
                        $completedEnd = Carbon::parse($task->end_at)->setTimezone($timezone);
                        if ($completedEnd->lessThanOrEqualTo($completedStart)) {
                            unset($effective[$id]);
                        }
                    }
                } elseif (!$task->start_at || !$task->end_at) {
                    $reason = '任务没有完整计划时间';
                } else {
                    $oldStart = Carbon::parse($task->start_at)->setTimezone($timezone);
                    $oldEnd = Carbon::parse($task->end_at)->setTimezone($timezone);
                    if ($oldEnd->lessThanOrEqualTo($oldStart)) {
                        $reason = '排期时间格式无效';
                        unset($effective[$id]);
                    }
                    $requiredStart = null;
                    if (!$reason) {
                        foreach ($predecessors[$id] ?? [] as $predecessorId) {
                            $predecessorTime = $effective[$predecessorId] ?? null;
                            if (!$predecessorTime) {
                                $reason = '前置任务没有完整计划时间';
                                // Do not let this task's unverified original date become
                                // a usable predecessor date for tasks farther downstream.
                                unset($effective[$id]);
                                break;
                            }
                            if (!$requiredStart || $predecessorTime[1]->greaterThan($requiredStart)) {
                                $requiredStart = $predecessorTime[1]->copy();
                            }
                        }
                    }
                    if (!$reason && $requiredStart) {
                        $requiredStart = self::addWorkingDays($requiredStart, $settings['buffer_days'], $settings['workdays'], $settings['holidays']);
                        while (!self::isWorkingDay($requiredStart, $settings['workdays'], $settings['holidays'])) {
                            $requiredStart->addDay();
                        }
                        if (!$reason && $oldStart->lessThan($requiredStart)) {
                            $canAdjust = self::canAdjustScheduledTask($project, $task, $permissionTaskMap, (int)User::userid(), $taskTimePermissions);
                            if ($canAdjust === null) {
                                // An active task can still reference an archived ancestor. Preserve
                                // the model's full hierarchy check when that ancestor was not in the snapshot.
                                try {
                                    ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_TIME, $task);
                                    $canAdjust = true;
                                } catch (ApiException $e) {
                                    $canAdjust = false;
                                }
                            }
                            if (!$canAdjust) {
                                $reason = '当前用户无权调整该任务计划时间';
                                unset($effective[$id]);
                            }
                            if (!$reason) {
                                $durationSeconds = max(0, self::wallClockDiffInSeconds($oldStart, $oldEnd));
                                $newEnd = self::shiftWallClock($requiredStart, $durationSeconds);
                                $effective[$id] = [$requiredStart->copy(), $newEnd];
                                self::appendTaskScheduleChanges($changes, $expandedSubtasks, $task, $oldStart, $oldEnd, $requiredStart, $newEnd, $timezone, '满足前置任务及缓冲时间');
                            }
                        }
                    }
                }
                if ($reason) {
                    $skipped[] = ['id' => (int)$task->id, 'name' => $task->name, 'reason' => $reason];
                }
            }
            foreach ($successors[$id] ?? [] as $nextId) {
                if (!isset($affected[$nextId])) continue;
                $indegree[$nextId]--;
                if ($indegree[$nextId] === 0) {
                    $queue->insert((int)$nextId, -(int)$nextId);
                }
            }
        }
        if ($processed !== count($affected)) {
            throw new ApiException('依赖关系存在循环，无法生成排期预演');
        }

        if ($anchorOldStart !== $anchorStart->toDateTimeString() || $anchorOldEnd !== $anchorEnd->toDateTimeString()) {
            self::appendTaskScheduleChanges($changes, $expandedSubtasks, $anchor,
                $anchor->start_at ? Carbon::parse($anchor->start_at)->setTimezone($timezone) : null,
                $anchor->end_at ? Carbon::parse($anchor->end_at)->setTimezone($timezone) : null,
                $anchorStart, $anchorEnd, $timezone, '调整排期起点');
        }

        $snapshot = [
            'project_id' => $projectId,
            'project_updated_at' => $project->updated_at?->toDateTimeString(),
            'schedule_revision' => (int)($project->task_schedule_revision ?: 1),
            'settings' => $settings,
            'task_time_permissions' => $taskTimePermissions,
            'tasks' => $tasks->map(fn (ProjectTask $task) => [
                (int)$task->id,
                (int)$task->parent_id,
                (string)$task->name,
                (int)$task->loop,
                $task->start_at?->toDateTimeString(),
                $task->end_at?->toDateTimeString(),
                $task->loop_at?->toDateTimeString(),
                $task->complete_at?->toDateTimeString(),
                $task->updated_at?->toDateTimeString(),
                $task->taskUser
                    ->map(fn (ProjectTaskUser $taskUser) => [(int)$taskUser->userid, (int)$taskUser->owner])
                    ->sortBy(fn (array $assignment) => sprintf('%010d:%d', $assignment[0], $assignment[1]))
                    ->values()
                    ->all(),
            ])->all(),
            'permission_ancestors' => $permissionAncestors->sortBy('id')->values()->map(fn (ProjectTask $task) => [
                (int)$task->id,
                (int)$task->parent_id,
                $task->updated_at?->toDateTimeString(),
                $task->taskUser
                    ->map(fn (ProjectTaskUser $taskUser) => [(int)$taskUser->userid, (int)$taskUser->owner])
                    ->sortBy(fn (array $assignment) => sprintf('%010d:%d', $assignment[0], $assignment[1]))
                    ->values()
                    ->all(),
            ])->all(),
            'edges' => $edges->map(fn (ProjectTaskDependency $edge) => [(int)$edge->id, (int)$edge->predecessor_task_id, (int)$edge->successor_task_id])->all(),
            'anchor' => [$anchorTaskId, $anchorStart->toDateTimeString(), $anchorEnd->toDateTimeString()],
            // Bind the exact user-visible preview outcome, including expanded
            // subtasks and skip reasons. Otherwise a subtask created or rescheduled
            // after preview could be silently added to the apply-time recomputation.
            'changes' => $changes,
            'skipped' => $skipped,
        ];
        $previewHash = hash('sha256', json_encode($snapshot, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        return [
            'project_id' => $projectId,
            'anchor_task_id' => $anchorTaskId,
            'preview_hash' => $previewHash,
            'changes' => $changes,
            'skipped' => $skipped,
            'conflicts' => count($skipped),
            'workdays' => $settings['workdays'],
            'holidays' => $settings['holidays'],
            'buffer_days' => $settings['buffer_days'],
            'mode' => $settings['mode'],
        ];
    }

    public static function apply(int $projectId, int $anchorTaskId, string $startAt, string $endAt, string $previewHash, string $requestId): array
    {
        if (!preg_match('/^[A-Za-z0-9._:-]{8,64}$/', $requestId) || !preg_match('/^[a-f0-9]{64}$/', $previewHash)) {
            throw new ApiException('排期批次参数无效');
        }
        $requestPayloadHash = self::requestPayloadHash($projectId, $anchorTaskId, $startAt, $endAt, $previewHash);
        $userId = (int)User::userid();
        [$batch, $tasks, $replayed] = DB::transaction(function () use ($projectId, $anchorTaskId, $startAt, $endAt, $previewHash, $requestId, $requestPayloadHash, $userId) {
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($projectId);
            self::assertManager($project);
            ProjectTask::query()->where('project_id', $projectId)->whereNull('archived_at')
                ->orderBy('id')->lockForUpdate()->get(['id']);
            $existing = ProjectTaskScheduleBatch::query()
                ->where('project_id', $projectId)
                ->where('request_id', $requestId)
                ->lockForUpdate()
                ->first();
            if ($existing) {
                if ($existing->preview_hash !== $previewHash
                    || ($existing->request_payload_hash && !hash_equals($existing->request_payload_hash, $requestPayloadHash))) {
                    throw new ApiException('排期请求编号已用于其他预演');
                }
                if ($existing->status !== 'applied') throw new ApiException('该排期批次已撤销，请重新预演');
                // Bind rows created before this field existed when they are first replayed.
                if (!$existing->request_payload_hash) {
                    $existing->request_payload_hash = $requestPayloadHash;
                    $existing->save();
                }
                // A previous request may have committed the batch but failed before
                // publishing task updates. Replaying the same idempotent request
                // republishes state updates without applying any dates again.
                return [$existing, self::batchTaskIds($existing), true];
            }
            $preview = self::preview($projectId, $anchorTaskId, $startAt, $endAt);
            if (!hash_equals($preview['preview_hash'], $previewHash)) {
                throw new ApiException('排期预演已过期，请重新计算');
            }
            $timezone = self::settingsData($project)['timezone'];
            $applied = [];
            foreach ($preview['changes'] as $change) {
                $task = ProjectTask::query()->where('project_id', $projectId)->whereKey($change['id'])->lockForUpdate()->firstOrFail();
                if (empty($change['schedule_parent_id'])) {
                    ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_TIME, $task);
                }
                if (self::formatProjectTime($task->start_at, $timezone) !== $change['before']['start_at']
                    || self::formatProjectTime($task->end_at, $timezone) !== $change['before']['end_at']) {
                    throw new ApiException('排期任务已发生变化，请重新预演');
                }
                if (array_key_exists('loop_at', $change['before'])
                    && $task->loop_at?->toDateTimeString() !== $change['before']['loop_at']) {
                    throw new ApiException('排期任务已发生变化，请重新预演');
                }
                if (array_key_exists('loop', $change['before']) && (int)$task->loop !== (int)$change['before']['loop']) {
                    throw new ApiException('排期任务已发生变化，请重新预演');
                }
                $task->start_at = self::storageTime($change['after']['start_at'], $timezone);
                $task->end_at = self::storageTime($change['after']['end_at'], $timezone);
                if (array_key_exists('loop_at', $change['after'])) {
                    $task->loop_at = $change['after']['loop_at']
                        ? Carbon::parse($change['after']['loop_at'], config('app.timezone', 'UTC'))
                        : null;
                }
                $task->save();
                $task->addLog('自动排期', [
                    'request_id' => $requestId,
                    'reason' => $change['reason'] ?? null,
                    'change' => [
                        ['type' => 'value', 'data' => self::scheduleRangeLabel($change['before'])],
                        ['type' => 'value', 'data' => self::scheduleRangeLabel($change['after'])],
                    ],
                ], $userId);
                $applied[] = (int)$task->id;
            }
            $batch = ProjectTaskScheduleBatch::createInstance([
                'project_id' => $projectId,
                'created_by' => $userId,
                'request_id' => $requestId,
                'preview_hash' => $previewHash,
                'request_payload_hash' => $requestPayloadHash,
                'timezone' => self::settingsData($project)['timezone'],
                'status' => 'applied',
                'changes' => $preview['changes'],
            ]);
            $batch->save();
            $project->task_schedule_revision = max(1, (int)$project->task_schedule_revision) + 1;
            $project->save();
            return [$batch, $applied, false];
        }, 3);
        self::publishTaskUpdates($tasks, !$replayed);
        return ['batch_id' => (int)$batch->id, 'status' => $batch->status, 'applied_count' => count($batch->changes ?: [])];
    }

    private static function requestPayloadHash(int $projectId, int $anchorTaskId, string $startAt, string $endAt, string $previewHash): string
    {
        return hash('sha256', json_encode([
            $projectId,
            $anchorTaskId,
            $startAt,
            $endAt,
            $previewHash,
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    }

    public static function undo(int $projectId, int $batchId): array
    {
        if ($projectId <= 0 || $batchId <= 0) throw new ApiException('参数错误');
        $userId = (int)User::userid();
        [$tasks, $replayed] = DB::transaction(function () use ($projectId, $batchId, $userId) {
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($projectId);
            self::assertManager($project);
            ProjectTask::query()->where('project_id', $projectId)->whereNull('archived_at')
                ->orderBy('id')->lockForUpdate()->get(['id']);
            $batch = ProjectTaskScheduleBatch::query()
                ->where('project_id', $projectId)
                ->whereKey($batchId)
                ->lockForUpdate()
                ->first();
            if (!$batch) throw new ApiException('排期批次不存在');
            if ($batch->status === 'undone') {
                // Match apply() recovery: the database undo can commit even if the
                // process stops before its websocket update is published.
                return [self::batchTaskIds($batch), true];
            }

            $updated = [];
            foreach ($batch->changes ?: [] as $change) {
                $task = ProjectTask::query()->where('project_id', $projectId)->whereKey($change['id'])->lockForUpdate()->first();
                $timezone = $batch->timezone ?: self::settingsData($project)['timezone'];
                if (!$task || self::formatProjectTime($task->start_at, $timezone) !== $change['after']['start_at']
                    || self::formatProjectTime($task->end_at, $timezone) !== $change['after']['end_at']) {
                    throw new ApiException('部分任务在批次后已被修改，不能安全撤销');
                }
                if (array_key_exists('loop_at', $change['after'])
                    && $task->loop_at?->toDateTimeString() !== $change['after']['loop_at']) {
                    throw new ApiException('部分任务在批次后已被修改，不能安全撤销');
                }
                if (array_key_exists('loop', $change['after']) && (int)$task->loop !== (int)$change['after']['loop']) {
                    throw new ApiException('部分任务在批次后已被修改，不能安全撤销');
                }
                if (empty($change['schedule_parent_id'])) {
                    ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_TIME, $task);
                }
                $task->start_at = self::storageTime($change['before']['start_at'], $timezone);
                $task->end_at = self::storageTime($change['before']['end_at'], $timezone);
                if (array_key_exists('loop_at', $change['before'])) {
                    $task->loop_at = $change['before']['loop_at']
                        ? Carbon::parse($change['before']['loop_at'], config('app.timezone', 'UTC'))
                        : null;
                }
                $task->save();
                $task->addLog('撤销自动排期', [
                    'batch_id' => (int)$batch->id,
                    'change' => [
                        ['type' => 'value', 'data' => self::scheduleRangeLabel($change['after'])],
                        ['type' => 'value', 'data' => self::scheduleRangeLabel($change['before'])],
                    ],
                ], $userId);
                $updated[] = (int)$task->id;
            }
            $batch->status = 'undone';
            $batch->undone_at = now();
            $batch->save();
            $project->task_schedule_revision = max(1, (int)$project->task_schedule_revision) + 1;
            $project->save();
            return [$updated, false];
        }, 3);
        self::publishTaskUpdates($tasks, !$replayed);
        return ['batch_id' => $batchId, 'status' => 'undone', 'restored_count' => count($tasks)];
    }

    private static function settingsData(Project $project): array
    {
        $configuredWorkdays = Base::json2array($project->task_schedule_workdays);
        $workdays = [];
        if (is_array($configuredWorkdays)) {
            foreach ($configuredWorkdays as $day) {
                $day = filter_var($day, FILTER_VALIDATE_INT);
                if ($day !== false && $day >= 1 && $day <= 7) $workdays[] = $day;
            }
        }
        $workdays = array_values(array_unique($workdays));
        sort($workdays);
        if (!$workdays) $workdays = [1, 2, 3, 4, 5];
        $holidays = self::normalizeHolidays(Base::json2array($project->task_schedule_holidays), true);
        $timezone = $project->task_schedule_timezone ?: config('app.timezone', 'UTC');
        if (!in_array($timezone, timezone_identifiers_list(), true)) $timezone = config('app.timezone', 'UTC');
        return [
            'mode' => in_array($project->task_schedule_mode, ['manual', 'automatic'], true) ? $project->task_schedule_mode : 'manual',
            'workdays' => $workdays,
            'holidays' => $holidays,
            'timezone' => $timezone,
            'buffer_days' => max(0, min(30, (int)$project->task_schedule_buffer_days)),
            'revision' => max(1, (int)$project->task_schedule_revision),
        ];
    }

    private static function batchTaskIds(ProjectTaskScheduleBatch $batch): array
    {
        return array_values(array_unique(array_filter(array_map(
            fn ($change) => is_array($change) ? (int)($change['id'] ?? 0) : 0,
            $batch->changes ?: []
        ), fn ($taskId) => $taskId > 0)));
    }

    private static function scheduleRangeLabel(array $range): string
    {
        return ($range['start_at'] ?? '—') . ' → ' . ($range['end_at'] ?? '—');
    }

    private static function appendTaskScheduleChanges(array &$changes, int &$expandedSubtasks, ProjectTask $root, ?Carbon $oldStart, ?Carbon $oldEnd, Carbon $newStart, Carbon $newEnd, string $timezone, string $reason): void
    {
        $beforeStart = $oldStart?->toDateTimeString();
        $beforeEnd = $oldEnd?->toDateTimeString();
        $afterStart = $newStart->toDateTimeString();
        $afterEnd = $newEnd->toDateTimeString();
        if ($beforeStart !== $afterStart || $beforeEnd !== $afterEnd) {
            $change = [
                'id' => (int)$root->id,
                'name' => $root->name,
                'before' => ['start_at' => $beforeStart, 'end_at' => $beforeEnd],
                'after' => ['start_at' => $afterStart, 'end_at' => $afterEnd],
                'reason' => $reason,
                'owner_userids' => $root->taskUser
                    ->where('owner', 1)
                    ->pluck('userid')
                    ->map(fn ($uid) => (int)$uid)
                    ->sort()
                    ->values()
                    ->all(),
            ];
            if ($root->loop && (int)$root->parent_id === 0) {
                $loopTask = clone $root;
                $loopTask->start_at = self::storageTime($afterStart, $timezone);
                $loopTask->end_at = self::storageTime($afterEnd, $timezone);
                $loopTask->refreshLoop();
                $change['before']['loop_at'] = $root->loop_at?->toDateTimeString();
                $change['after']['loop_at'] = $loopTask->loop_at?->toDateTimeString();
                $change['before']['loop'] = (int)$root->loop;
                $change['after']['loop'] = (int)$root->loop;
            }
            $changes[] = $change;
            if (count($changes) > self::MAX_SCHEDULE_CHANGES) {
                throw new ApiException('单次排期变更数量超出上限，请缩小变更范围');
            }
        }
        if (!$oldStart) return;
        $delta = self::wallClockDiffInSeconds($oldStart, $newStart);
        if ($delta === 0) return;

        $frontier = [(int)$root->id];
        $depth = 0;
        while ($frontier) {
            if (++$depth > 5) throw new ApiException('子任务格式错误');
            $remainingSubtasks = self::MAX_SCHEDULE_CHANGES - $expandedSubtasks;
            $children = collect();
            foreach (array_chunk($frontier, 500) as $parentIds) {
                $remainingQueryRows = $remainingSubtasks - $children->count();
                $batch = ProjectTask::query()->where('project_id', $root->project_id)
                    ->whereIn('parent_id', $parentIds)->whereNull('archived_at')->orderBy('id')
                    ->limit(max(1, $remainingQueryRows + 1))
                    ->get(['id', 'parent_id', 'name', 'start_at', 'end_at']);
                $children = $children->concat($batch);
                if ($children->count() > $remainingSubtasks) {
                    throw new ApiException('单次排期子任务数量超出上限，请缩小变更范围');
                }
            }
            $children = $children->sortBy('id')->values();
            $frontier = [];
            foreach ($children as $child) {
                $expandedSubtasks++;
                $frontier[] = (int)$child->id;
                if (!$child->start_at || !$child->end_at) continue;
                $childStart = Carbon::parse($child->start_at)->setTimezone($timezone);
                $childEnd = Carbon::parse($child->end_at)->setTimezone($timezone);
                $changes[] = [
                    'id' => (int)$child->id,
                    'name' => $child->name,
                    'schedule_parent_id' => (int)$root->id,
                    'before' => ['start_at' => $childStart->toDateTimeString(), 'end_at' => $childEnd->toDateTimeString()],
                    'after' => ['start_at' => self::shiftWallClock($childStart, $delta)->toDateTimeString(), 'end_at' => self::shiftWallClock($childEnd, $delta)->toDateTimeString()],
                    'reason' => '跟随主任务调整',
                    'owner_userids' => [],
                ];
                if (count($changes) > self::MAX_SCHEDULE_CHANGES) {
                    throw new ApiException('单次排期变更数量超出上限，请缩小变更范围');
                }
            }
        }
    }

    private static function addWorkingDays(Carbon $date, int $days, array $workdays, array $holidays): Carbon
    {
        $result = $date->copy();
        $added = 0;
        while ($added < $days) {
            $result->addDay();
            if (self::isWorkingDay($result, $workdays, $holidays)) $added++;
        }
        return $result;
    }

    private static function isWorkingDay(Carbon $date, array $workdays, array $holidays): bool
    {
        return in_array($date->dayOfWeekIso, $workdays, true)
            && !in_array($date->toDateString(), $holidays, true);
    }

    /**
     * Count configured working days inclusively. Capacity reports use the same
     * calendar as automatic scheduling while keeping long task ranges O(weeks).
     */
    public static function workingDaysInRange(Carbon $start, Carbon $end, array $workdays, array $holidays = []): int
    {
        $first = $start->copy()->startOfDay();
        $last = $end->copy()->startOfDay();
        if ($first->greaterThan($last) || !$workdays) return 0;

        $calendarDays = (int)$first->diffInDays($last) + 1;
        $fullWeeks = intdiv($calendarDays, 7);
        $days = $fullWeeks * count($workdays);
        $remainder = $calendarDays % 7;
        $firstRemainderDay = $first->copy()->addDays($fullWeeks * 7);
        for ($offset = 0; $offset < $remainder; $offset++) {
            if (in_array($firstRemainderDay->copy()->addDays($offset)->dayOfWeekIso, $workdays, true)) $days++;
        }
        foreach ($holidays as $holiday) {
            $holidayDate = Carbon::createFromFormat('!Y-m-d', $holiday, $first->getTimezone());
            if ($holidayDate && !$holidayDate->lessThan($first) && !$holidayDate->greaterThan($last)
                && in_array($holidayDate->dayOfWeekIso, $workdays, true)) {
                $days--;
            }
        }
        return $days;
    }

    private static function normalizeHolidays($holidays, bool $tolerateInvalidStoredValues = false): array
    {
        if (!is_array($holidays)) {
            if ($tolerateInvalidStoredValues) return [];
            throw new ApiException('节假日格式无效');
        }
        if (count($holidays) > 366) {
            if ($tolerateInvalidStoredValues) return [];
            throw new ApiException('节假日最多设置366个日期');
        }
        $normalized = [];
        foreach ($holidays as $holiday) {
            if (!is_string($holiday) || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $holiday)) {
                if ($tolerateInvalidStoredValues) continue;
                throw new ApiException('节假日日期格式无效');
            }
            try {
                $date = Carbon::createFromFormat('!Y-m-d', $holiday, 'UTC');
            } catch (\Throwable $e) {
                $date = false;
            }
            if (!$date || $date->format('Y-m-d') !== $holiday) {
                if ($tolerateInvalidStoredValues) continue;
                throw new ApiException('节假日日期格式无效');
            }
            $normalized[] = $holiday;
        }
        $normalized = array_values(array_unique($normalized));
        sort($normalized);
        return $normalized;
    }

    /**
     * Compare local wall-clock values without letting DST change the elapsed seconds.
     */
    private static function wallClockDiffInSeconds(Carbon $from, Carbon $to): int
    {
        $fromWall = Carbon::createFromFormat('!Y-m-d H:i:s', $from->format('Y-m-d H:i:s'), 'UTC');
        $toWall = Carbon::createFromFormat('!Y-m-d H:i:s', $to->format('Y-m-d H:i:s'), 'UTC');
        return (int)$fromWall->diffInSeconds($toWall, false);
    }

    /** Shift in a timezone-free wall clock, then interpret the result in the project timezone. */
    private static function shiftWallClock(Carbon $value, int $deltaSeconds): Carbon
    {
        $wallTime = Carbon::createFromFormat('!Y-m-d H:i:s', $value->format('Y-m-d H:i:s'), 'UTC');
        $wallTime->addSeconds($deltaSeconds);
        $expectedWallTime = $wallTime->format('Y-m-d H:i:s');
        $shifted = Carbon::createFromFormat('!Y-m-d H:i:s', $expectedWallTime, $value->getTimezone());
        // Some local times do not exist when clocks move forward for DST. Carbon
        // normalizes those values; reject them instead of silently changing task duration.
        if (!$shifted || $shifted->format('Y-m-d H:i:s') !== $expectedWallTime) {
            throw new ApiException('排期时间格式无效');
        }
        return $shifted;
    }

    private static function formatProjectTime($date, string $timezone): ?string
    {
        return $date ? Carbon::parse($date)->setTimezone($timezone)->toDateTimeString() : null;
    }

    private static function parseProjectTime(string $date, string $timezone): Carbon
    {
        if (!preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/', $date)) {
            throw new ApiException('排期时间格式无效');
        }
        $parsed = Carbon::createFromFormat('!Y-m-d H:i:s', $date, $timezone);
        if (!$parsed || $parsed->format('Y-m-d H:i:s') !== $date) {
            throw new ApiException('排期时间格式无效');
        }
        return $parsed;
    }

    private static function storageTime(?string $date, string $timezone): ?Carbon
    {
        return $date === null ? null : Carbon::parse($date, $timezone)->setTimezone(config('app.timezone', 'UTC'));
    }

    private static function assertManager(Project $project): void
    {
        if (!$project->isOwner((int)User::userid())) {
            throw new ApiException('仅项目负责人或管理员可执行自动排期');
        }
    }

    /**
     * Reuse the task assignments already eager-loaded for the preview instead of
     * issuing owner/assistant existence queries for every affected task.
     * Returns null when an ancestor is outside the active-task snapshot so the
     * canonical permission check can resolve that edge case.
     */
    private static function canAdjustScheduledTask(Project $project, ProjectTask $task, $taskMap, int $userId, array $permissions): ?bool
    {
        if (in_array(ProjectPermission::PERMISSIONS['project_leader'], $permissions, true) && $project->owner) {
            return true;
        }
        // preview() already requires manager status, so this branch exists for managers
        // whose project role no longer includes the leader permission. Do not reuse this
        // helper outside the manager-only scheduling flow, or it would let any member
        // adjust dates.
        if (in_array(ProjectPermission::PERMISSIONS['project_member'], $permissions, true)) {
            return true;
        }
        $canUseOwner = in_array(ProjectPermission::PERMISSIONS['task_leader'], $permissions, true);
        $canUseAssistant = in_array(ProjectPermission::PERMISSIONS['task_assist'], $permissions, true);
        if (!$canUseOwner && !$canUseAssistant) {
            return false;
        }

        $row = $task;
        $first = true;
        $visited = [];
        while ($row) {
            $rowId = (int)$row->id;
            if (isset($visited[$rowId])) return null;
            $visited[$rowId] = true;
            if (!$row->relationLoaded('taskUser')) return null;
            if ($first && $canUseOwner && !empty($row->owner)) return true;
            foreach ($row->taskUser as $assignment) {
                if ((int)$assignment->userid !== $userId) continue;
                if ($canUseOwner && (int)$assignment->owner === 1) return true;
                if ($canUseAssistant && (int)$assignment->owner === 0) return true;
            }
            $parentId = (int)$row->parent_id;
            if ($parentId <= 0) return false;
            $row = $taskMap->get($parentId);
            if (!$row) return null;
            $first = false;
        }
        return false;
    }

    private static function publishTaskUpdates(array $taskIds, bool $notifyOwners = true): void
    {
        foreach (array_unique($taskIds) as $taskId) {
            try {
                // This runs after the schedule transaction has committed. Any lookup,
                // serialization or push failure must not turn a successful write into
                // an API error; the idempotent replay path can republish task state.
                $task = ProjectTask::find((int)$taskId);
                if (!$task) continue;
                try {
                    $task->pushMsg('update', ProjectTask::oneTask($task->id));
                } catch (\Throwable $e) {
                    report($e);
                }
                if ($notifyOwners) {
                    try {
                        $task->taskPush(null, 3);
                    } catch (\Throwable $e) {
                        report($e);
                    }
                }
            } catch (\Throwable $e) {
                report($e);
            }
        }
    }
}
