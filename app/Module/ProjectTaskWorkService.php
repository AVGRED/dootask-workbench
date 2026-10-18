<?php

namespace App\Module;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectPermission;
use App\Models\ProjectTask;
use App\Models\ProjectTaskUser;
use App\Models\ProjectTaskVisibilityUser;
use App\Models\ProjectTaskWorkLog;
use App\Models\ProjectUser;
use App\Models\User;
use Carbon\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Database\QueryException;

class ProjectTaskWorkService
{
    public static function setEstimate(int $projectId, int $taskId, $minutes): array
    {
        $minutes = filter_var($minutes, FILTER_VALIDATE_INT);
        if ($minutes === false || $minutes < 0 || $minutes > 1000000) throw new ApiException('预估工时须为 0 至 1000000 分钟');
        $userId = (int)User::userid();
        $changed = false;
        DB::transaction(function () use ($projectId, $taskId, $minutes, $userId, &$changed) {
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($projectId);
            $task = ProjectTask::query()->where('project_id', $projectId)->whereKey($taskId)->lockForUpdate()->firstOrFail();
            ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $task);
            $previousMinutes = (int)($task->estimated_minutes ?: 0);
            if ($previousMinutes === $minutes) return;
            $task->estimated_minutes = $minutes ?: null;
            $task->save();
            $task->addLog('更新预估工时', [
                'estimated_minutes' => $minutes,
                'change' => [
                    ['type' => 'value', 'data' => $previousMinutes . ' min'],
                    ['type' => 'value', 'data' => $minutes . ' min'],
                ],
            ], $userId);
            $changed = true;
        });
        if ($changed) self::publishTaskUpdate($taskId);
        return self::taskSummary($projectId, $taskId);
    }

    public static function logs(int $projectId, int $taskId, int $page = 1, int $perPage = 50): array
    {
        if ($page < 1 || $perPage < 1 || $perPage > 100) throw new ApiException('工时历史分页参数无效');
        // Anyone who can see the task may read its recorded time; the task detail
        // page is opened by assignees and project members who cannot edit it.
        // Writing/deleting still requires TASK_UPDATE (see addLog/deleteLog).
        $task = ProjectTask::userTask($taskId, null);
        if ((int)$task->project_id !== $projectId) throw new ApiException('任务不存在');
        $query = ProjectTaskWorkLog::query()->where('project_id', $projectId)->where('task_id', $taskId);
        $total = (clone $query)->count();
        if ($total === 0 || $page > (int)ceil($total / $perPage)) {
            return ['items' => [], 'total' => $total, 'page' => $page, 'per_page' => $perPage];
        }
        $logs = $query->with('user:userid,nickname,userimg')
            ->orderByDesc('work_date')->orderByDesc('id')->forPage($page, $perPage)->get()
            ->map(fn (ProjectTaskWorkLog $log) => self::logData($log))->all();
        return ['items' => $logs, 'total' => $total, 'page' => $page, 'per_page' => $perPage];
    }

    public static function projectToday(int $projectId): string
    {
        Project::userProject($projectId);
        $timezone = ProjectTaskScheduleService::settings($projectId)['timezone'];
        return Carbon::now($timezone)->toDateString();
    }

    public static function addLog(int $projectId, int $taskId, array $input): array
    {
        $minutes = filter_var($input['minutes'] ?? null, FILTER_VALIDATE_INT);
        $requestId = $input['request_id'] ?? '';
        $requestedDate = $input['work_date'] ?? null;
        $rawNote = $input['note'] ?? '';
        if (!is_string($rawNote)) throw new ApiException('工时记录格式无效（分钟 1-1440，备注最多 500 字）');
        $note = trim($rawNote);
        if (!is_string($requestId) || !preg_match('/^[A-Za-z0-9._:-]{8,64}$/', $requestId)
            || $minutes === false || $minutes < 1 || $minutes > 1440
            || ($requestedDate !== null && (!is_string($requestedDate) || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $requestedDate)))
            || mb_strlen($note) > 500) {
            throw new ApiException('工时记录格式无效（分钟 1-1440，备注最多 500 字）');
        }
        $userId = (int)User::userid();
        $requestPayloadHash = self::requestPayloadHash($projectId, $taskId, $minutes, $requestedDate, $note);
        $created = false;
        $date = null;
        try {
            $log = DB::transaction(function () use ($projectId, $taskId, $requestId, $requestPayloadHash, $minutes, $requestedDate, $note, $userId, &$created, &$date) {
                Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
                $project = Project::userProject($projectId);
                $timezone = ProjectTaskScheduleService::settings($projectId)['timezone'];
                $date = $requestedDate ?? Carbon::now($timezone)->toDateString();
                try {
                    $parsed = Carbon::createFromFormat('!Y-m-d', $date, $timezone);
                    if (!$parsed || $parsed->format('Y-m-d') !== $date) throw new \RuntimeException();
                } catch (\Throwable $e) {
                    throw new ApiException('工时日期无效');
                }
                $task = ProjectTask::query()->where('project_id', $projectId)->whereKey($taskId)->lockForUpdate()->firstOrFail();
                ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $task);
                $existing = ProjectTaskWorkLog::withTrashed()->where('project_id', $projectId)->where('userid', $userId)
                    ->where('request_id', $requestId)->first();
                if ($existing) {
                    // Keep deleted rows as idempotency tombstones so a delayed client retry
                    // cannot recreate a worklog after a user intentionally removed it.
                    if ($existing->trashed()) throw new ApiException('工时请求编号已用于其他记录');
                    if ((int)$existing->task_id !== $taskId || (int)$existing->minutes !== $minutes
                        || ($requestedDate !== null && $existing->work_date?->format('Y-m-d') !== $requestedDate)
                        || (string)$existing->note !== ($note ?: '')
                        || ($existing->request_payload_hash && !hash_equals($existing->request_payload_hash, $requestPayloadHash))) {
                        throw new ApiException('工时请求编号已用于其他记录');
                    }
                    if (!$existing->request_payload_hash) {
                        $existing->request_payload_hash = $requestPayloadHash;
                        $existing->save();
                    }
                    return $existing;
                }
                $log = ProjectTaskWorkLog::createInstance([
                    'project_id' => $projectId, 'task_id' => $taskId, 'userid' => $userId, 'request_id' => $requestId,
                    'request_payload_hash' => $requestPayloadHash,
                    'minutes' => $minutes, 'work_date' => $date, 'note' => $note ?: null,
                ]);
                $log->save();
                $task->addLog('登记实际工时', [
                    'work_log_id' => $log->id,
                    'minutes' => $minutes,
                    'work_date' => $date,
                    'change' => [
                        ['type' => 'value', 'data' => $minutes . ' min'],
                        ['type' => 'value', 'data' => $date],
                    ],
                ], $userId);
                $created = true;
                return $log;
            });
        } catch (QueryException $exception) {
            $errorInfo = $exception->errorInfo ?? [];
            // Constraint diagnostics differ across MySQL, PostgreSQL and SQLite. On a
            // uniqueness SQLSTATE, resolve the specific idempotency row instead of
            // depending on the driver's constraint-name wording.
            $isConstraintViolation = in_array((string)($errorInfo[0] ?? ''), ['23000', '23505'], true);
            if (!$isConstraintViolation) throw $exception;
            $log = ProjectTaskWorkLog::withTrashed()->where('project_id', $projectId)->where('userid', $userId)
                ->where('request_id', $requestId)->first();
            if (!$log) throw $exception;
            if ($log->trashed() || (int)$log->task_id !== $taskId || (int)$log->minutes !== $minutes
                || ($requestedDate !== null && $log->work_date?->format('Y-m-d') !== $requestedDate)
                || (string)$log->note !== ($note ?: '')
                || ($log->request_payload_hash && !hash_equals($log->request_payload_hash, $requestPayloadHash))) {
                throw new ApiException('工时请求编号已用于其他记录');
            }
            if (!$log->request_payload_hash) {
                $log->request_payload_hash = $requestPayloadHash;
                $log->save();
            }
        }
        if ($created) self::publishTaskUpdate($taskId);
        return ['log' => self::logData($log->load('user:userid,nickname,userimg')), 'summary' => self::taskSummary($projectId, $taskId)];
    }

    public static function deleteLog(int $projectId, int $logId): array
    {
        $userId = (int)User::userid();
        $taskId = DB::transaction(function () use ($projectId, $logId, $userId) {
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($projectId);
            $log = ProjectTaskWorkLog::query()->where('project_id', $projectId)->whereKey($logId)->lockForUpdate()->firstOrFail();
            $task = ProjectTask::query()->where('project_id', $projectId)->whereKey($log->task_id)->lockForUpdate()->firstOrFail();
            if ((int)$log->userid !== $userId && !$project->isOwner($userId)) throw new ApiException('只能删除自己的工时记录');
            ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $task);
            $minutes = (int)$log->minutes;
            $workDate = $log->work_date?->format('Y-m-d') ?: (string)$log->work_date;
            $log->delete();
            $task->addLog('删除实际工时记录', [
                'work_log_id' => $logId,
                'minutes' => $minutes,
                'work_date' => $workDate,
                'change' => [
                    ['type' => 'value', 'data' => $minutes . ' min'],
                    ['type' => 'value', 'data' => $workDate],
                ],
            ], $userId);
            return (int)$task->id;
        });
        self::publishTaskUpdate($taskId);
        return self::taskSummary($projectId, $taskId);
    }

    /**
     * Move the full actual-work history with its task tree. Request IDs are unique
     * per project and user, so conflicts in the destination receive a stable internal key.
     *
     * @param array<int> $taskIds
     */
    public static function handleProjectMove(int $sourceProjectId, int $targetProjectId, array $taskIds): void
    {
        $taskIds = array_values(array_unique(array_filter(array_map('intval', $taskIds), fn ($id) => $id > 0)));
        if (!$taskIds || $sourceProjectId === $targetProjectId) {
            return;
        }

        foreach (array_chunk($taskIds, 1000) as $taskIdBatch) {
            ProjectTaskWorkLog::withTrashed()
                ->where('project_id', $sourceProjectId)
                ->whereIn('task_id', $taskIdBatch)
                ->orderBy('id')
                ->chunkById(500, function ($logs) use ($sourceProjectId, $targetProjectId) {
                    foreach ($logs as $log) {
                        $requestId = (string)$log->request_id;
                        $conflict = ProjectTaskWorkLog::withTrashed()
                            ->where('project_id', $targetProjectId)
                            ->where('userid', $log->userid)
                            ->where('request_id', $requestId)
                            ->exists();
                        if ($conflict) {
                            $requestId = 'move:' . $sourceProjectId . ':' . $log->id;
                            $suffix = 0;
                            while (ProjectTaskWorkLog::withTrashed()
                                ->where('project_id', $targetProjectId)
                                ->where('userid', $log->userid)
                                ->where('request_id', $requestId)
                                ->exists()) {
                                $suffix++;
                                $requestId = 'move:' . $sourceProjectId . ':' . $log->id . ':' . $suffix;
                            }
                        }

                        $log->project_id = $targetProjectId;
                        $log->request_id = $requestId;
                        // The payload hash includes the project ID. Let the first valid replay
                        // in the destination bind a new hash for the moved record.
                        $log->request_payload_hash = null;
                        $log->save();
                    }
                });
        }
    }

    private static function publishTaskUpdate(int $taskId): void
    {
        try {
            $task = ProjectTask::find($taskId);
            if ($task) $task->pushMsg('work', ProjectTask::oneTask($taskId));
        } catch (\Throwable $exception) {
            // The database write has already committed. A websocket failure must not
            // make the API report that the worklog or estimate itself failed.
            report($exception);
        }
    }

    public static function taskSummary(int $projectId, int $taskId): array
    {
        $task = ProjectTask::query()->where('project_id', $projectId)->whereKey($taskId)->firstOrFail();
        $logged = (int)ProjectTaskWorkLog::where('project_id', $projectId)
            ->where('task_id', $taskId)
            ->sum('minutes');
        $estimate = (int)($task->estimated_minutes ?: 0);
        return ['task_id' => $taskId, 'estimated_minutes' => $estimate, 'logged_minutes' => $logged,
            'remaining_minutes' => max(0, $estimate - $logged), 'overrun_minutes' => max(0, $logged - $estimate)];
    }

    public static function capacity(int $projectId, string $from, string $to): array
    {
        $project = Project::userProject($projectId);
        $userId = (int)User::userid();
        if (!$project->isOwner($userId) && !ProjectUser::where('project_id', $projectId)->where('userid', $userId)->exists()) {
            throw new ApiException('无权查看项目工时汇总');
        }
        try {
            $fromDate = Carbon::createFromFormat('!Y-m-d', $from);
            $toDate = Carbon::createFromFormat('!Y-m-d', $to);
            if (!$fromDate || !$toDate || $fromDate->format('Y-m-d') !== $from || $toDate->format('Y-m-d') !== $to) throw new \RuntimeException();
        } catch (\Throwable $e) { throw new ApiException('日期范围无效'); }
        if (!$fromDate || !$toDate || $fromDate->gt($toDate) || $fromDate->diffInDays($toDate) > 92) throw new ApiException('日期范围最多 93 天');
        $schedule = ProjectTaskScheduleService::settings($projectId);
        $timezone = $schedule['timezone'];
        $workdays = $schedule['workdays'];
        $holidays = $schedule['holidays'] ?? [];
        $rangeStart = Carbon::parse($from, $timezone)->startOfDay();
        $rangeEnd = Carbon::parse($to, $timezone)->endOfDay();
        $availableMinutes = ProjectTaskScheduleService::workingDaysInRange($rangeStart, $rangeEnd, $workdays, $holidays) * 480;
        $storageStart = $rangeStart->copy()->setTimezone(config('app.timezone', 'UTC'));
        $storageEnd = $rangeEnd->copy()->setTimezone(config('app.timezone', 'UTC'));
        // Archived tasks keep their actual-work history in reports. Soft-deleted tasks stay
        // hidden by the model scope until restored; only the planned-work query excludes archive.
        $visibleTaskIds = ProjectTask::query()->select('project_tasks.id')->where('project_tasks.project_id', $projectId)
            ->where(function ($query) use ($userId, $project) {
                if ($project->isOwner($userId)) return;
                $query->where('project_tasks.visibility', 1)
                    ->orWhereExists(fn ($sub) => $sub->selectRaw('1')->from((new ProjectTaskUser())->getTable())
                        ->whereColumn('project_task_users.task_id', 'project_tasks.id')->where('project_task_users.userid', $userId)
                        ->whereIn('project_task_users.owner', [0, 1]))
                    ->orWhereExists(fn ($sub) => $sub->selectRaw('1')->from((new ProjectTaskVisibilityUser())->getTable())
                        ->whereColumn('project_task_visibility_users.task_id', 'project_tasks.id')->where('project_task_visibility_users.userid', $userId));
                self::addAncestorAssigneeVisibility($query, $userId);
            });
        $tasks = ProjectTask::query()->where('project_id', $projectId)->where('parent_id', 0)->whereNull('archived_at')
            ->select(['project_tasks.id', 'project_tasks.estimated_minutes', 'project_tasks.start_at', 'project_tasks.end_at'])
            ->whereNotNull('estimated_minutes')->whereNotNull('start_at')->whereNotNull('end_at')
            ->where('start_at', '<=', $storageEnd)->where('end_at', '>=', $storageStart)
            ->whereIn('id', clone $visibleTaskIds)
            ->with(['taskUser' => fn ($query) => $query->where('owner', 1)]);
        $memberIds = ProjectUser::where('project_id', $projectId)->pluck('userid')->map(fn ($id) => (int)$id)->all();
        $memberIds[] = (int)$project->owner_userid;
        $capacityMemberIds = array_fill_keys(array_unique(array_filter($memberIds)), true);
        $members = [];
        foreach (array_keys($capacityMemberIds) as $id) {
            $members[$id] = ['userid' => $id, 'capacity_member' => true, 'estimated_minutes' => 0, 'logged_minutes' => 0, 'task_count' => 0];
        }
        $tasks->chunkById(500, function ($taskRows) use (&$members, $capacityMemberIds, $timezone, $workdays, $holidays, $rangeStart, $rangeEnd) {
            foreach ($taskRows as $task) {
                $owners = $task->taskUser->where('owner', 1)->values();
                if (!$owners->count()) continue;
                $taskStart = Carbon::parse($task->start_at)->setTimezone($timezone)->startOfDay();
                $taskEnd = Carbon::parse($task->end_at)->setTimezone($timezone)->startOfDay();
                $taskRangeStart = $rangeStart->copy()->startOfDay();
                $taskRangeEnd = $rangeEnd->copy()->startOfDay();
                $fullDays = max(1, ProjectTaskScheduleService::workingDaysInRange($taskStart, $taskEnd, $workdays, $holidays));
                $includedStart = $taskStart->greaterThan($taskRangeStart) ? $taskStart : $taskRangeStart;
                $includedEnd = $taskEnd->lessThan($taskRangeEnd) ? $taskEnd : $taskRangeEnd;
                $includedDays = ProjectTaskScheduleService::workingDaysInRange($includedStart, $includedEnd, $workdays, $holidays);
                $periodEstimate = (int)round((int)$task->estimated_minutes * min(1, $includedDays / $fullDays));
                $perOwnerEstimate = (int)floor($periodEstimate / $owners->count());
                $ownerRemainder = $periodEstimate % $owners->count();
                foreach ($owners as $ownerIndex => $owner) {
                    $id = (int)$owner->userid;
                    if (!isset($members[$id])) $members[$id] = [
                        'userid' => $id, 'capacity_member' => isset($capacityMemberIds[$id]),
                        'estimated_minutes' => 0, 'logged_minutes' => 0, 'task_count' => 0,
                    ];
                    $members[$id]['estimated_minutes'] += $perOwnerEstimate + ($ownerIndex < $ownerRemainder ? 1 : 0);
                    $members[$id]['task_count']++;
                }
            }
        }, 'id');
        $logs = ProjectTaskWorkLog::query()->select('userid', DB::raw('SUM(minutes) as minutes'))
            ->where('project_id', $projectId)->whereBetween('work_date', [$from, $to])
            ->whereIn('task_id', clone $visibleTaskIds)->groupBy('userid')->get();
        foreach ($logs as $log) {
            $id = (int)$log->userid;
            if (!isset($members[$id])) $members[$id] = [
                'userid' => $id, 'capacity_member' => isset($capacityMemberIds[$id]),
                'estimated_minutes' => 0, 'logged_minutes' => 0, 'task_count' => 0,
            ];
            $members[$id]['logged_minutes'] = (int)$log->minutes;
        }
        $userIds = array_keys($members);
        $users = User::whereIn('userid', $userIds)->get(['userid', 'nickname', 'userimg'])->keyBy('userid');
        $rows = [];
        foreach ($members as $id => $member) {
            $member['user'] = self::userData($users->get($id));
            $member['available_minutes'] = $member['capacity_member'] ? $availableMinutes : 0;
            $member['remaining_minutes'] = max(0, $member['estimated_minutes'] - $member['logged_minutes']);
            $member['overrun_minutes'] = max(0, $member['logged_minutes'] - $member['estimated_minutes']);
            $member['capacity_utilization_percent'] = $member['available_minutes'] > 0
                ? (int)round($member['estimated_minutes'] / $member['available_minutes'] * 100)
                : null;
            $rows[] = $member;
        }
        return ['from' => $from, 'to' => $to, 'members' => $rows,
            'totals' => ['estimated_minutes' => array_sum(array_column($rows, 'estimated_minutes')),
                'logged_minutes' => array_sum(array_column($rows, 'logged_minutes')),
                'remaining_minutes' => array_sum(array_column($rows, 'remaining_minutes')),
                'overrun_minutes' => array_sum(array_column($rows, 'overrun_minutes')),
                'available_minutes' => array_sum(array_column($rows, 'available_minutes'))],
            'capacity_baseline' => ['minutes_per_workday' => 480, 'workdays' => $workdays, 'holidays' => $holidays, 'timezone' => $timezone]];
    }

    /**
     * Task owner and assistant permissions inherit from ancestors. Include worklogs
     * on child tasks in a member's capacity totals when that member is assigned higher
     * in the same (maximum five-level) task tree.
     */
    private static function addAncestorAssigneeVisibility($query, int $userId): void
    {
        $taskUserTable = (new ProjectTaskUser())->getTable();
        for ($depth = 1; $depth <= 5; $depth++) {
            $query->orWhereExists(function ($ancestorQuery) use ($depth, $userId, $taskUserTable) {
                $ancestorQuery->selectRaw('1')->from('project_tasks as capacity_parent_1');
                for ($level = 1; $level <= $depth; $level++) {
                    $alias = "capacity_parent_{$level}";
                    if ($level === 1) {
                        $ancestorQuery->whereColumn($alias . '.id', 'project_tasks.parent_id');
                    } else {
                        $previousAlias = 'capacity_parent_' . ($level - 1);
                        $ancestorQuery->join("project_tasks as {$alias}", $alias . '.id', '=', $previousAlias . '.parent_id');
                    }
                    $ancestorQuery->whereNull($alias . '.deleted_at')
                        ->whereColumn($alias . '.project_id', 'project_tasks.project_id');
                }
                $lastAlias = 'capacity_parent_' . $depth;
                $ancestorQuery->whereExists(function ($assignmentQuery) use ($lastAlias, $userId, $taskUserTable) {
                    $assignmentQuery->selectRaw('1')->from($taskUserTable . ' as capacity_parent_assignment')
                        ->whereColumn('capacity_parent_assignment.task_id', $lastAlias . '.id')
                        ->where('capacity_parent_assignment.userid', $userId)
                        ->whereIn('capacity_parent_assignment.owner', [0, 1]);
                });
            });
        }
    }

    private static function logData(ProjectTaskWorkLog $log): array
    {
        return ['id' => (int)$log->id, 'task_id' => (int)$log->task_id, 'userid' => (int)$log->userid,
            'user' => self::userData($log->user), 'minutes' => (int)$log->minutes, 'work_date' => $log->work_date?->format('Y-m-d'),
            'note' => $log->note, 'created_at' => $log->created_at?->toDateTimeString()];
    }

    /**
     * Expose a stable user summary for the worklog UI. DooTask stores the display
     * name in users.nickname and the avatar in users.userimg; the frontend consumes
     * name/avatar, so keep that mapping here instead of selecting non-existent columns.
     */
    private static function userData(?User $user): ?array
    {
        if (!$user) {
            return null;
        }
        return [
            'userid' => (int)$user->userid,
            'name' => $user->nickname,
            'avatar' => $user->userimg,
        ];
    }

    private static function requestPayloadHash(int $projectId, int $taskId, int $minutes, ?string $requestedDate, string $note): string
    {
        return hash('sha256', json_encode([
            $projectId,
            $taskId,
            $minutes,
            $requestedDate === null ? ['work_date' => 'default'] : ['work_date' => $requestedDate],
            $note,
        ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    }
}
