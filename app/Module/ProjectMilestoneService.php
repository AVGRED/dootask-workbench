<?php

namespace App\Module;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectMilestone;
use App\Models\ProjectMilestoneReminderAttempt;
use App\Models\ProjectPermission;
use App\Models\ProjectTask;
use App\Models\User;
use App\Models\ProjectUser;
use App\Models\WebSocketDialogMsg;
use Carbon\Carbon;
use Illuminate\Support\Facades\DB;
use Throwable;

class ProjectMilestoneService
{
    public static function lists(int $projectId): array
    {
        $project = Project::findForDepartmentView($projectId);
        $canManage = $project->isOwner(User::userid());
        $milestones = ProjectMilestone::query()
            ->where('project_id', $project->id)
            ->orderBy('sort')
            ->orderBy('target_at')
            ->orderBy('id')
            ->get();
        $milestoneIds = $milestones->pluck('id')->map(fn ($id) => (int)$id)->all();
        $now = Carbon::now()->toDateTimeString();
        $tasksByMilestone = $milestoneIds
            ? self::visibleTaskQuery((int)$project->id)
                ->whereIn('project_tasks.milestone_id', $milestoneIds)
                ->whereNull('project_tasks.archived_at')
                ->selectRaw('project_tasks.milestone_id, project_tasks.flow_item_name, COUNT(*) AS task_count, SUM(CASE WHEN project_tasks.complete_at IS NOT NULL THEN 1 ELSE 0 END) AS completed_count, SUM(CASE WHEN project_tasks.complete_at IS NULL AND project_tasks.end_at IS NOT NULL AND project_tasks.end_at < ? THEN 1 ELSE 0 END) AS overdue_count', [$now])
                ->groupBy('project_tasks.milestone_id', 'project_tasks.flow_item_name')
                ->get()
                ->groupBy(fn ($item) => (int)$item->milestone_id)
            : collect();

        return [
            'milestones' => $milestones
                ->map(function (ProjectMilestone $milestone) use ($tasksByMilestone, $canManage) {
                    $stats = $tasksByMilestone->get((int)$milestone->id, collect());
                    $taskCount = (int)$stats->sum('task_count');
                    $completedCount = (int)$stats->sum('completed_count');
                    $overdueCount = (int)$stats->sum('overdue_count');
                    $flowStatuses = $stats
                        ->groupBy(fn ($item) => $item->flow_item_name ?: '')
                        ->map(function ($statusTasks, $flowItemName) {
                            $parts = explode('|', $flowItemName);
                            return [
                                'name' => $parts[1] ?? '未设置状态',
                                'color' => $parts[2] ?? '',
                                'count' => (int)$statusTasks->sum('task_count'),
                            ];
                        })
                        ->sortByDesc('count')
                        ->values()
                        ->all();

                    $data = array_merge(self::serialize($milestone), [
                        'task_count' => $taskCount,
                        'completed_count' => $completedCount,
                        'overdue_count' => $overdueCount,
                        'progress_percent' => $taskCount > 0 ? (int)floor($completedCount / $taskCount * 100) : 0,
                        'flow_statuses' => $flowStatuses,
                    ]);
                    if ($canManage) {
                        $data['reminded_at'] = $milestone->reminded_at?->toDateTimeString();
                        $data['reminder_attempt_count'] = (int)$milestone->reminder_attempt_count;
                        $data['reminder_last_attempt_at'] = $milestone->reminder_last_attempt_at?->toDateTimeString();
                        $data['reminder_last_error_code'] = $milestone->reminder_last_error_code;
                    }
                    return $data;
                })
                ->all(),
            'can_manage' => $canManage,
        ];
    }

    public static function tasks(int $projectId, int $milestoneId, int $page = 1, int $perPage = 30): array
    {
        $project = Project::findForDepartmentView($projectId);
        $milestone = ProjectMilestone::query()
            ->where('project_id', $project->id)
            ->whereKey($milestoneId)
            ->first();
        if (!$milestone) {
            throw new ApiException('里程碑不存在');
        }

        $query = self::visibleTaskQuery((int)$project->id)
            ->with('projectColumn')
            ->where('project_tasks.milestone_id', $milestone->id)
            ->whereNull('project_tasks.archived_at');
        $total = (clone $query)->count('project_tasks.id');
        $page = max(1, $page);
        $perPage = min(100, max(1, $perPage));
        if ($total === 0 || $page > (int)ceil($total / $perPage)) {
            return [
                'list' => [],
                'total' => $total,
                'page' => $page,
                'per_page' => $perPage,
            ];
        }
        $tasks = $query
            ->orderByRaw('project_tasks.end_at IS NULL')
            ->orderBy('project_tasks.end_at')
            ->orderBy('project_tasks.id')
            ->forPage($page, $perPage)
            ->get();

        return [
            'list' => $tasks->map(function (ProjectTask $task) {
                $flowParts = explode('|', $task->flow_item_name ?: '');
                return [
                    'id' => (int)$task->id,
                    'name' => $task->name,
                    'complete_at' => $task->complete_at?->toDateTimeString(),
                    'end_at' => $task->end_at?->toDateTimeString(),
                    'overdue' => (bool)$task->overdue,
                    'column_name' => $task->projectColumn?->name,
                    'flow_item_name' => $flowParts[1] ?? $task->flow_item_name,
                    'flow_item_status' => $flowParts[0] ?? '',
                    'flow_item_color' => $flowParts[2] ?? '',
                ];
            })->all(),
            'total' => $total,
            'page' => $page,
            'per_page' => $perPage,
        ];
    }

    public static function reminderAttempts(int $projectId, int $milestoneId, int $page = 1, int $perPage = 20): array
    {
        if ($page < 1 || $perPage < 1 || $perPage > 100) {
            throw new ApiException('参数错误');
        }
        $project = Project::userProject($projectId);
        self::assertCanManage($project);
        $milestone = ProjectMilestone::query()
            ->where('project_id', $projectId)
            ->whereKey($milestoneId)
            ->first();
        if (!$milestone) {
            throw new ApiException('里程碑不存在');
        }
        $query = ProjectMilestoneReminderAttempt::query()
            ->where('project_id', $projectId)
            ->where('milestone_id', $milestoneId);
        $total = (clone $query)->count();
        $items = $total === 0 || $page > (int)ceil($total / $perPage)
            ? []
            : $query->orderByDesc('id')->forPage($page, $perPage)->get()
                ->map(fn (ProjectMilestoneReminderAttempt $attempt) => [
                    'id' => (int)$attempt->id,
                    'reminder_generation' => (int)$attempt->reminder_generation,
                    'target_at' => $attempt->target_at?->format('Y-m-d'),
                    'request_id' => $attempt->request_id,
                    'status' => $attempt->status,
                    'result_code' => $attempt->result_code,
                    'attempted_at' => $attempt->attempted_at?->toDateTimeString(),
                ])->all();

        return ['items' => $items, 'total' => $total, 'page' => $page, 'per_page' => $perPage];
    }

    public static function save(int $projectId, int $milestoneId, string $name, string $description, ?string $targetAt, string $status, int $reminderDays = -1, ?int $ownerUserid = null): ProjectMilestone
    {
        $project = Project::userProject($projectId);
        self::assertCanManage($project);

        $name = trim($name);
        $description = trim($description);
        if (mb_strlen($name) < 1 || mb_strlen($name) > 100) {
            throw new ApiException('里程碑名称长度须为1至100个字符');
        }
        if (mb_strlen($description) > 500) {
            throw new ApiException('里程碑说明最多500个字符');
        }
        if (!in_array($status, ['open', 'completed'], true)) {
            throw new ApiException('里程碑状态无效');
        }
        if (!in_array($reminderDays, [-1, 0, 1, 3, 7], true)) {
            throw new ApiException('里程碑提醒设置无效');
        }
        if ($ownerUserid !== null && $ownerUserid < 0) {
            throw new ApiException('参数错误');
        }
        $date = null;
        if ($targetAt !== null && $targetAt !== '') {
            try {
                $parsed = Carbon::createFromFormat('!Y-m-d', $targetAt);
            } catch (Throwable) {
                throw new ApiException('目标日期格式错误');
            }
            if (!$parsed || $parsed->format('Y-m-d') !== $targetAt) {
                throw new ApiException('目标日期格式错误');
            }
            $date = $parsed->toDateString();
        }
        if ($reminderDays >= 0 && !$date) {
            throw new ApiException('开启里程碑提醒前请先设置目标日期');
        }

        [$milestone, $isNew, $materialChange] = DB::transaction(function () use ($project, $milestoneId, $name, $description, $date, $status, $reminderDays, $ownerUserid) {
            Project::query()->whereKey($project->id)->lockForUpdate()->first();
            $project = Project::userProject((int)$project->id);
            self::assertCanManage($project);
            $isNew = $milestoneId <= 0;
            $oldName = '';
            $oldDescription = '';
            $oldTargetAt = null;
            $oldStatus = '';
            $oldReminderDays = -1;
            $oldOwnerUserid = 0;
            if (!$isNew) {
                $milestone = ProjectMilestone::query()
                    ->where('project_id', $project->id)
                    ->whereKey($milestoneId)
                    ->lockForUpdate()
                    ->first();
                if (!$milestone) {
                    throw new ApiException('里程碑不存在');
                }
                $oldName = $milestone->name;
                $oldDescription = (string)$milestone->description;
                $oldTargetAt = $milestone->target_at ? Carbon::parse($milestone->target_at)->toDateString() : null;
                $oldStatus = $milestone->status;
                $oldReminderDays = (int)$milestone->reminder_days;
                $oldOwnerUserid = (int)$milestone->owner_userid;
            } else {
                $milestone = ProjectMilestone::createInstance([
                    'project_id' => $project->id,
                    'created_by' => User::userid(),
                    'sort' => (int)ProjectMilestone::where('project_id', $project->id)->max('sort') + 1,
                    'reminder_days' => $reminderDays,
                    'owner_userid' => null,
                ]);
            }

            $newOwnerUserid = $ownerUserid === null ? $oldOwnerUserid : $ownerUserid;
            if ($newOwnerUserid > 0 && $newOwnerUserid !== $oldOwnerUserid
                && !ProjectUser::query()->where('project_id', $project->id)->where('userid', $newOwnerUserid)->exists()) {
                throw new ApiException('参数错误');
            }

            $materialChange = $isNew || $oldName !== $name || $oldDescription !== $description
                || $oldTargetAt !== $date || $oldStatus !== $status || $oldReminderDays !== $reminderDays
                || $oldOwnerUserid !== $newOwnerUserid;
            if (!$materialChange) {
                return [$milestone, false, false];
            }

            $milestone->name = $name;
            $milestone->description = $description ?: null;
            $milestone->owner_userid = $newOwnerUserid > 0 ? $newOwnerUserid : null;
            $milestone->target_at = $date;
            $milestone->status = $status;
            $milestone->reminder_days = $reminderDays;
            if ($isNew || $oldTargetAt !== $date || $oldReminderDays !== $reminderDays || $oldStatus !== $status) {
                $milestone->reminded_at = null;
                $milestone->reminder_generation = (int)$milestone->reminder_generation + 1;
                $milestone->reminder_attempt_count = 0;
                $milestone->reminder_last_attempt_at = null;
                $milestone->reminder_last_error_code = null;
            }
            $milestone->save();
            $project->addLog($isNew ? '创建项目里程碑' : '更新项目里程碑', [
                'before' => $isNew ? null : [
                    'name' => $oldName,
                    'description' => $oldDescription,
                    'target_at' => $oldTargetAt,
                    'status' => $oldStatus,
                    'reminder_days' => $oldReminderDays,
                    'owner_userid' => $oldOwnerUserid,
                ],
                'after' => [
                    'name' => $name,
                    'description' => $description ?: null,
                    'target_at' => $date,
                    'status' => $status,
                    'reminder_days' => $reminderDays,
                    'owner_userid' => $newOwnerUserid,
                ],
                'change' => [
                    ['type' => 'value', 'data' => ($isNew ? '—' : $oldName) . ' → ' . $name],
                    ['type' => 'value', 'data' => ($oldTargetAt ?: '—') . ' → ' . ($date ?: '—')],
                    ['type' => 'value', 'data' => ($isNew ? '—' : $oldStatus) . ' → ' . $status],
                    ['type' => 'value', 'data' => ($isNew ? '—' : $oldReminderDays) . ' → ' . $reminderDays . ' days'],
                    ['type' => 'value', 'data' => ($isNew ? '—' : $oldOwnerUserid) . ' → ' . $newOwnerUserid],
                ],
            ]);

            return [$milestone, $isNew, true];
        });

        if ($materialChange) {
            self::publishProjectUpdate($project);
            self::notifyProject($project, ($isNew ? '新增项目里程碑：' : '项目里程碑有重要更新：') . $name);
        }

        return $milestone;
    }

    public static function delete(int $projectId, int $milestoneId): void
    {
        $project = Project::userProject($projectId);
        self::assertCanManage($project);
        $milestone = ProjectMilestone::query()
            ->where('project_id', $project->id)
            ->whereKey($milestoneId)
            ->first();
        if (!$milestone) {
            throw new ApiException('里程碑不存在');
        }

        $taskIds = [];
        $deletedMilestoneName = '';
        DB::transaction(function () use ($milestone, $project, &$taskIds, &$deletedMilestoneName) {
            Project::query()->whereKey($project->id)->lockForUpdate()->first();
            $project = Project::userProject((int)$project->id);
            self::assertCanManage($project);
            // The initial lookup only validates the request. Re-read under the
            // project lock so concurrent delete requests cannot both log and
            // announce a deletion using the same stale milestone instance.
            $lockedMilestone = ProjectMilestone::query()
                ->where('project_id', $project->id)
                ->whereKey($milestone->id)
                ->lockForUpdate()
                ->first();
            if (!$lockedMilestone) {
                throw new ApiException('里程碑不存在');
            }
            $deletedMilestoneName = $lockedMilestone->name;
            // Keep the per-row save: it fires the task observers that keep the search
            // index and websocket payloads in sync. A bulk UPDATE would be faster but
            // would silently skip them, so a large milestone may hold this transaction
            // open longer than usual; batch reindexing is a separate change.
            ProjectTask::withTrashed()
                ->where('project_id', $project->id)
                ->where('milestone_id', $lockedMilestone->id)
                ->chunkById(100, function ($tasks) use (&$taskIds) {
                    foreach ($tasks as $task) {
                        $task->milestone_id = null;
                        $task->save();
                        $task->addLog('取消关联里程碑');
                        $taskIds[] = (int)$task->id;
                    }
                });
            $lockedMilestone->delete();
            $project->addLog('删除项目里程碑', ['change' => [$deletedMilestoneName]]);
        });

        // Keep task events outside the transaction so clients never observe a rollback.
        self::publishProjectUpdate($project);
        foreach (array_chunk($taskIds, 500) as $taskIdChunk) {
            ProjectTask::withTrashed()
                ->with('project')
                ->whereIn('id', $taskIdChunk)
                ->chunkById(100, function ($tasks) {
                    foreach ($tasks as $task) {
                        self::publishTaskUpdate($task);
                    }
                });
        }
        self::notifyProject($project, '项目里程碑已删除：' . $deletedMilestoneName);
    }

    /** @param array<int> $milestoneIds */
    public static function sort(int $projectId, array $milestoneIds): void
    {
        $project = Project::userProject($projectId);
        self::assertCanManage($project);

        DB::transaction(function () use ($project, $milestoneIds) {
            Project::query()->whereKey($project->id)->lockForUpdate()->first();
            $project = Project::userProject((int)$project->id);
            self::assertCanManage($project);
            $currentIds = ProjectMilestone::query()
                ->where('project_id', $project->id)
                ->orderBy('id')
                ->lockForUpdate()
                ->pluck('id')
                ->map(fn ($id) => (int)$id)
                ->all();
            $requestedIds = array_map('intval', $milestoneIds);

            if (count($requestedIds) !== count(array_unique($requestedIds))
                || count($requestedIds) !== count($currentIds)
                || array_diff($requestedIds, $currentIds)
                || array_diff($currentIds, $requestedIds)) {
                throw new ApiException('参数错误');
            }

            foreach ($requestedIds as $sort => $milestoneId) {
                ProjectMilestone::query()
                    ->where('project_id', $project->id)
                    ->whereKey($milestoneId)
                    ->update(['sort' => $sort]);
            }
            $project->addLog('调整项目里程碑顺序');
        });

        self::publishProjectUpdate($project);
    }

    public static function assign(int $taskId, int $milestoneId): ?ProjectMilestone
    {
        $task = ProjectTask::userTask($taskId);
        $project = Project::userProject((int)$task->project_id);
        if (!isset(self::visibleTasks((int)$project->id, [$taskId])[$taskId])) {
            throw new ApiException('无任务权限', ['task_id' => $taskId], -4002);
        }
        ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $task);

        $result = DB::transaction(function () use ($task, $project, $milestoneId) {
            // Serialize assignment with milestone deletion so a concurrently deleted
            // milestone cannot leave an invalid task reference.
            Project::query()->whereKey($project->id)->lockForUpdate()->first();
            $project = Project::userProject((int)$project->id);
            $lockedTask = ProjectTask::query()->whereKey($task->id)->lockForUpdate()->first();
            if (!$lockedTask || (int)$lockedTask->project_id !== (int)$project->id) {
                throw new ApiException('任务不存在');
            }
            if (!isset(self::visibleTasks((int)$project->id, [(int)$lockedTask->id])[(int)$lockedTask->id])) {
                throw new ApiException('无任务权限', ['task_id' => (int)$lockedTask->id], -4002);
            }
            ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $lockedTask);
            $milestone = null;
            if ($milestoneId > 0) {
                $milestone = ProjectMilestone::query()
                    ->where('project_id', $project->id)
                    ->whereKey($milestoneId)
                    ->lockForUpdate()
                    ->first();
                if (!$milestone) {
                    throw new ApiException('里程碑不存在或不属于当前项目');
                }
            }
            if ((int)$lockedTask->milestone_id === $milestoneId) {
                return [$milestone, false];
            }

            $lockedTask->milestone_id = $milestone?->id;
            $lockedTask->save();
            $lockedTask->addLog($milestone ? '关联里程碑' : '取消关联里程碑', [
                'change' => [$milestone?->name ?: ''],
            ]);
            $task->refresh();
            return [$milestone, true];
        });
        [$milestone, $changed] = $result;
        if (!$changed) {
            return $milestone;
        }
        self::publishTaskUpdate($task);

        return $milestone;
    }

    /** @param array<int> $taskIds @return array<int, ProjectTask> */
    private static function visibleTasks(int $projectId, array $taskIds): array
    {
        return self::visibleTaskQuery($projectId)
            ->whereIn('project_tasks.id', $taskIds)
            ->get()
            ->keyBy(fn ($task) => (int)$task->id)
            ->all();
    }

    private static function visibleTaskQuery(int $projectId)
    {
        $userid = User::userid();
        return ProjectTask::query()
            ->where('project_tasks.project_id', $projectId)
            ->where(function ($query) use ($userid) {
                $query->where('project_tasks.visibility', 1)
                    ->orWhereExists(function ($subquery) use ($userid) {
                        $subquery->selectRaw('1')
                            ->from('project_users')
                            ->whereColumn('project_users.project_id', 'project_tasks.project_id')
                            ->where('project_users.userid', $userid)
                            ->whereIn('project_users.owner', [ProjectUser::OWNER_PRIMARY, ProjectUser::OWNER_DEPUTY]);
                    })
                    ->orWhereExists(function ($subquery) use ($userid) {
                        $subquery->selectRaw('1')
                            ->from('project_task_users')
                            ->where('project_task_users.userid', $userid)
                            ->where(function ($taskUser) {
                                $taskUser->whereColumn('project_task_users.task_id', 'project_tasks.id')
                                    ->orWhereColumn('project_task_users.task_pid', 'project_tasks.id');
                            });
                    })
                    ->orWhereExists(function ($subquery) use ($userid) {
                        $subquery->selectRaw('1')
                            ->from('project_task_visibility_users')
                            ->whereColumn('project_task_visibility_users.task_id', 'project_tasks.id')
                            ->where('project_task_visibility_users.userid', $userid);
                    });
                // Match task__lists: visibility granted to any ancestor applies to
                // descendants, up to the supported five-level subtask depth.
                for ($depth = 1; $depth <= 5; $depth++) {
                    $query->orWhereExists(function ($ancestorQuery) use ($userid, $depth) {
                        $parentAlias = 'milestone_task_ancestor_1';
                        $ancestorQuery->selectRaw('1')->from('project_tasks as ' . $parentAlias);
                        for ($level = 2; $level <= $depth; $level++) {
                            $nextParentAlias = 'milestone_task_ancestor_' . $level;
                            $ancestorQuery->join('project_tasks as ' . $nextParentAlias, $nextParentAlias . '.id', '=', $parentAlias . '.parent_id');
                            $parentAlias = $nextParentAlias;
                        }
                        $ancestorQuery->whereColumn('milestone_task_ancestor_1.id', 'project_tasks.parent_id');
                        for ($level = 1; $level <= $depth; $level++) {
                            $ancestorAlias = 'milestone_task_ancestor_' . $level;
                            $ancestorQuery->whereColumn($ancestorAlias . '.project_id', 'project_tasks.project_id')
                                ->whereNull($ancestorAlias . '.deleted_at');
                        }
                        $ancestorQuery->where(function ($memberQuery) use ($userid, $parentAlias) {
                                $memberQuery->whereExists(function ($taskUserQuery) use ($userid, $parentAlias) {
                                    $taskUserQuery->selectRaw('1')->from('project_task_users')
                                        ->whereColumn('project_task_users.task_id', $parentAlias . '.id')
                                        ->where('project_task_users.userid', $userid);
                                })->orWhereExists(function ($visibilityQuery) use ($userid, $parentAlias) {
                                    $visibilityQuery->selectRaw('1')->from('project_task_visibility_users')
                                        ->whereColumn('project_task_visibility_users.task_id', $parentAlias . '.id')
                                        ->where('project_task_visibility_users.userid', $userid);
                                });
                            });
                    });
                }
            });
    }

    private static function assertCanManage(Project $project): void
    {
        if (!$project->isOwner(User::userid())) {
            throw new ApiException('仅项目负责人和管理员可以管理里程碑', [], -4002);
        }
    }

    /**
     * 重要里程碑变更通过项目群聊发送弱提醒，不强制触发弹窗通知。
     */
    private static function notifyProject(Project $project, string $notice): void
    {
        if ((int)$project->dialog_id <= 0) {
            return;
        }
        try {
            WebSocketDialogMsg::sendMsg(null, (int)$project->dialog_id, 'notice', [
                'notice' => $notice,
            ]);
        } catch (Throwable $e) {
            report($e);
        }
    }

    private static function publishProjectUpdate(Project $project): void
    {
        try {
            $project->pushMsg('update');
        } catch (Throwable $e) {
            report($e);
        }
    }

    private static function publishTaskUpdate(ProjectTask $task): void
    {
        try {
            $task->pushMsg('update', null, null, false);
        } catch (Throwable $e) {
            report($e);
        }
    }

    private static function serialize(ProjectMilestone $milestone): array
    {
        return [
            'id' => (int)$milestone->id,
            'project_id' => (int)$milestone->project_id,
            'name' => $milestone->name,
            'description' => $milestone->description,
            'owner_userid' => (int)$milestone->owner_userid,
            'target_at' => $milestone->target_at,
            'status' => $milestone->status,
            'reminder_days' => (int)$milestone->reminder_days,
            'sort' => (int)$milestone->sort,
        ];
    }
}
