<?php

namespace App\Module;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectPermission;
use App\Models\ProjectTask;
use App\Models\ProjectTaskDependency;
use App\Models\WebSocketDialogMsg;
use App\Models\ProjectUser;
use App\Models\User;
use Carbon\Carbon;
use Illuminate\Support\Facades\DB;
use Throwable;

class ProjectTaskDependencyService
{
    /**
     * Return incoming and outgoing dependencies visible to the current user.
     */
    public static function lists(int $taskId): array
    {
        $task = ProjectTask::findForDepartmentView($taskId, null);
        $visibleTasks = self::visibleTasks((int)$task->project_id, [$taskId]);
        if (!isset($visibleTasks[$taskId])) {
            throw new ApiException('无任务权限', ['task_id' => $taskId], -4002);
        }
        if (!self::isEnabled((int)$task->project_id)) {
            return ['task_id' => $taskId, 'list' => []];
        }
        $relations = ProjectTaskDependency::query()
            ->where('project_id', $task->project_id)
            ->where(function ($query) use ($taskId) {
                $query->where('predecessor_task_id', $taskId)
                    ->orWhere('successor_task_id', $taskId);
            })
            ->orderBy('id')
            ->get();

        $relatedTaskIds = $relations->map(function ($relation) use ($taskId) {
            return (int)$relation->predecessor_task_id === $taskId
                ? (int)$relation->successor_task_id
                : (int)$relation->predecessor_task_id;
        })->unique()->all();
        $visibleTasks = self::visibleTasksChunked((int)$task->project_id, $relatedTaskIds, ['project', 'projectColumn']);

        $list = [];
        foreach ($relations as $relation) {
            $isPredecessor = (int)$relation->successor_task_id === $taskId;
            $relatedTaskId = $isPredecessor
                ? (int)$relation->predecessor_task_id
                : (int)$relation->successor_task_id;
            $relatedTask = $visibleTasks[$relatedTaskId] ?? null;
            if (!$relatedTask) {
                // Never disclose the existence or details of a task the caller cannot access.
                continue;
            }

            $flowParts = explode('|', $relatedTask->flow_item_name ?: '');
            $list[] = [
                'id' => (int)$relation->id,
                'direction' => $isPredecessor ? 'predecessor' : 'successor',
                'task' => [
                    'id' => (int)$relatedTask->id,
                    'name' => $relatedTask->name,
                    'project_id' => (int)$relatedTask->project_id,
                    'project_name' => $relatedTask->project?->name,
                    'column_name' => $relatedTask->projectColumn?->name,
                    'start_at' => $relatedTask->start_at?->toDateTimeString(),
                    'end_at' => $relatedTask->end_at?->toDateTimeString(),
                    'complete_at' => $relatedTask->complete_at?->toDateTimeString(),
                    'archived_at' => $relatedTask->archived_at?->toDateTimeString(),
                    'flow_item_name' => $flowParts[1] ?? $relatedTask->flow_item_name,
                    'flow_item_status' => $flowParts[0] ?? '',
                    'flow_item_color' => $flowParts[2] ?? '',
                ],
            ];
        }

        return [
            'task_id' => $taskId,
            'list' => $list,
        ];
    }

    /**
     * Return dependencies touching the supplied rows and include only neighbors
     * that the caller may see. Neighbor summaries let schedule previews stay
     * accurate even when search or column filters hide the other Gantt row.
     */
    public static function projectLists(int $projectId, array $taskIds): array
    {
        $project = Project::findForDepartmentView($projectId);
        if (!self::isEnabled((int)$project->id)) {
            return [];
        }
        if (count($taskIds) > 5000) {
            throw new ApiException('甘特图任务数量超出限制');
        }
        $taskIds = array_values(array_unique(array_filter(array_map(function ($id) {
            if (!is_int($id) && !is_string($id)) {
                return 0;
            }
            $id = filter_var($id, FILTER_VALIDATE_INT);
            return $id !== false && $id > 0 ? $id : 0;
        }, $taskIds))));
        if (!$taskIds) {
            return [];
        }

        $selectedTaskIds = array_keys(self::visibleTasksChunked($projectId, $taskIds));
        if (!$selectedTaskIds) {
            return [];
        }

        // Fetch direct neighbors on either side of each selected task. The query
        // is chunked to stay below database parameter limits on large projects.
        $edges = collect();
        foreach (array_chunk($selectedTaskIds, 1000) as $selectedIds) {
            $chunkEdges = ProjectTaskDependency::query()
                ->where('project_id', $project->id)
                ->where(function ($query) use ($selectedIds) {
                    $query->whereIn('predecessor_task_id', $selectedIds)
                        ->orWhereIn('successor_task_id', $selectedIds);
                })
                ->limit(10001)
                ->get(['id', 'predecessor_task_id', 'successor_task_id']);
            if ($chunkEdges->count() > 10000) {
                throw new ApiException('甘特图关联依赖过多，请缩小筛选范围后重试');
            }
            $edges = $edges->merge($chunkEdges);
            $edges = $edges->unique('id')->values();
            if ($edges->count() > 10000) {
                throw new ApiException('甘特图关联依赖过多，请缩小筛选范围后重试');
            }
        }

        $candidateTaskIds = $edges->flatMap(fn ($edge) => [
            (int)$edge->predecessor_task_id,
            (int)$edge->successor_task_id,
        ])->unique()->values()->all();
        $visibleTasks = self::visibleTasksChunked($projectId, $candidateTaskIds);

        return $edges->filter(fn ($edge) => isset($visibleTasks[(int)$edge->predecessor_task_id], $visibleTasks[(int)$edge->successor_task_id]))
            ->sortBy('id')->values()
            ->map(function ($edge) use ($visibleTasks) {
                $predecessor = $visibleTasks[(int)$edge->predecessor_task_id];
                $successor = $visibleTasks[(int)$edge->successor_task_id];
                $summary = fn (ProjectTask $task) => [
                    'id' => (int)$task->id,
                    'name' => $task->name,
                    'start_at' => $task->start_at?->toDateTimeString(),
                    'end_at' => $task->end_at?->toDateTimeString(),
                ];
                return [
                    'id' => (int)$edge->id,
                    'predecessor_task_id' => (int)$edge->predecessor_task_id,
                    'successor_task_id' => (int)$edge->successor_task_id,
                    'predecessor' => $summary($predecessor),
                    'successor' => $summary($successor),
                ];
            })
            ->all();
    }

    /**
     * Summarize actionable dependency risks without exposing tasks hidden from the caller.
     */
    public static function riskSummary(int $projectId): array
    {
        $project = Project::findForDepartmentView($projectId);
        $empty = [
            'enabled' => self::isEnabled((int)$project->id),
            'schedule_conflicts' => 0,
            'overdue_blockers' => 0,
            'total_risk_edges' => 0,
            'truncated' => false,
            'items' => [],
        ];
        if (!$empty['enabled']) return $empty;

        $now = Carbon::now();
        $overdueItems = [];
        $conflictItems = [];
        ProjectTaskDependency::query()
            ->where('project_id', $project->id)
            ->orderBy('id')
            ->chunkById(500, function ($edges) use (&$empty, &$overdueItems, &$conflictItems, $project, $now) {
                $taskIds = $edges->flatMap(fn ($edge) => [
                    (int)$edge->predecessor_task_id,
                    (int)$edge->successor_task_id,
                ])->unique()->values()->all();
                $visibleTasks = self::visibleTasksChunked((int)$project->id, $taskIds);

                foreach ($edges as $edge) {
                    $predecessor = $visibleTasks[(int)$edge->predecessor_task_id] ?? null;
                    $successor = $visibleTasks[(int)$edge->successor_task_id] ?? null;
                    if (!$predecessor || !$successor || $predecessor->archived_at || $successor->archived_at
                        || $successor->complete_at) {
                        continue;
                    }

                    $types = [];
                    if ($predecessor->end_at && $successor->start_at
                        && $successor->start_at->lessThan($predecessor->end_at)) {
                        $types[] = 'schedule_conflict';
                        $empty['schedule_conflicts']++;
                    }
                    if (!$predecessor->complete_at && $predecessor->end_at
                        && $predecessor->end_at->lessThan($now)) {
                        $types[] = 'overdue_blocker';
                        $empty['overdue_blockers']++;
                    }
                    if (!$types) continue;

                    $item = [
                        'id' => (int)$edge->id,
                        'types' => $types,
                        'predecessor' => [
                            'id' => (int)$predecessor->id,
                            'name' => $predecessor->name,
                            'start_at' => $predecessor->start_at?->toDateTimeString(),
                            'end_at' => $predecessor->end_at?->toDateTimeString(),
                            'complete_at' => $predecessor->complete_at?->toDateTimeString(),
                        ],
                        'successor' => [
                            'id' => (int)$successor->id,
                            'name' => $successor->name,
                            'start_at' => $successor->start_at?->toDateTimeString(),
                            'end_at' => $successor->end_at?->toDateTimeString(),
                        ],
                    ];
                    $empty['total_risk_edges']++;
                    if (in_array('overdue_blocker', $types, true)) {
                        if (count($overdueItems) < 50) $overdueItems[] = $item;
                    } elseif (count($conflictItems) < 50) {
                        $conflictItems[] = $item;
                    }
                }
            }, 'id');

        $empty['items'] = array_merge($overdueItems, array_slice($conflictItems, 0, max(0, 50 - count($overdueItems))));
        $empty['truncated'] = $empty['total_risk_edges'] > count($empty['items']);
        return $empty;
    }

    /**
     * Add a finish-to-start dependency in the current task's project.
     * $direction is predecessor (selected task blocks current) or successor (current blocks selected task).
     */
    public static function add(int $taskId, int $relatedTaskId, string $direction): ProjectTaskDependency
    {
        if ($taskId <= 0 || $relatedTaskId <= 0 || $taskId === $relatedTaskId) {
            throw new ApiException('依赖任务参数无效');
        }
        if (!in_array($direction, ['predecessor', 'successor'], true)) {
            throw new ApiException('依赖方向无效');
        }

        $task = ProjectTask::userTask($taskId);
        $relatedTask = ProjectTask::userTask($relatedTaskId);
        if ((int)$task->project_id !== (int)$relatedTask->project_id) {
            throw new ApiException('目前只支持同一项目内的任务依赖');
        }
        if ((int)$task->parent_id > 0 || (int)$relatedTask->parent_id > 0) {
            throw new ApiException('目前不支持对子任务设置任务依赖');
        }

        $project = Project::userProject($task->project_id);
        self::assertEnabled($project);
        self::assertTasksVisible((int)$project->id, [$taskId, $relatedTaskId]);
        ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $task);
        ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $relatedTask);

        $predecessorId = $direction === 'predecessor' ? $relatedTaskId : $taskId;
        $successorId = $direction === 'predecessor' ? $taskId : $relatedTaskId;

        [$dependency, $predecessor, $successor] = DB::transaction(function () use ($taskId, $relatedTaskId, $project, $predecessorId, $successorId) {
            // Serialize graph edits for this project so simultaneous requests cannot create a cycle.
            Project::query()->whereKey($project->id)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($project->id);
            self::assertEnabled($project);

            // Re-read task state and permissions after waiting for the project lock.
            $task = ProjectTask::userTask($taskId);
            $relatedTask = ProjectTask::userTask($relatedTaskId);
            if ((int)$task->project_id !== (int)$project->id || (int)$relatedTask->project_id !== (int)$project->id) {
                throw new ApiException('目前只支持同一项目内的任务依赖');
            }
            if ((int)$task->parent_id > 0 || (int)$relatedTask->parent_id > 0) {
                throw new ApiException('目前不支持对子任务设置任务依赖');
            }
            self::assertTasksVisible((int)$project->id, [$taskId, $relatedTaskId]);
            ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $task);
            ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $relatedTask);

            $existing = ProjectTaskDependency::query()
                ->where('predecessor_task_id', $predecessorId)
                ->where('successor_task_id', $successorId)
                ->first();
            if ($existing) {
                return [$existing, null, null];
            }

            if (self::wouldCreateCycle((int)$project->id, $predecessorId, $successorId)) {
                throw new ApiException('添加依赖会形成循环，请调整任务关系');
            }

            $dependency = ProjectTaskDependency::createInstance([
                'project_id' => $project->id,
                'predecessor_task_id' => $predecessorId,
                'successor_task_id' => $successorId,
                'created_by' => User::userid(),
            ]);
            $dependency->save();

            $predecessor = $predecessorId === (int)$task->id ? $task : $relatedTask;
            $successor = $successorId === (int)$task->id ? $task : $relatedTask;
            // A task can become private after this relation is created. Keep its title
            // out of the other task's durable activity log to avoid leaking it later.
            $predecessor->addLog('添加后置依赖', [], User::userid());
            $successor->addLog('添加前置依赖', [], User::userid());

            return [$dependency, $predecessor, $successor];
        });

        // Publish only after the edge and both audit logs have committed.
        if ($predecessor) {
            self::publishRelationUpdate($predecessor);
            self::notifyTask($predecessor, '任务依赖关系已更新');
        }
        if ($successor) {
            self::publishRelationUpdate($successor);
            self::notifyTask($successor, '任务依赖关系已更新');
        }

        return $dependency;
    }

    public static function delete(int $taskId, int $dependencyId): void
    {
        $task = ProjectTask::userTask($taskId, null);
        $project = Project::userProject($task->project_id);
        self::assertEnabled($project);
        $dependency = ProjectTaskDependency::query()
            ->where('project_id', $project->id)
            ->where('id', $dependencyId)
            ->where(function ($query) use ($taskId) {
                $query->where('predecessor_task_id', $taskId)
                    ->orWhere('successor_task_id', $taskId);
            })
            ->first();
        if (!$dependency) {
            throw new ApiException('依赖不存在');
        }

        $predecessor = ProjectTask::userTask($dependency->predecessor_task_id, null);
        $successor = ProjectTask::userTask($dependency->successor_task_id, null);
        self::assertTasksVisible((int)$project->id, [$taskId, (int)$predecessor->id, (int)$successor->id]);
        ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $task);
        ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $predecessor);
        ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $successor);

        [$deleted, $predecessor, $successor] = DB::transaction(function () use ($project, $dependency, $taskId) {
            // Serialize removal with graph edits, then re-read the edge so concurrent deletes
            // cannot emit duplicate audit logs and update notifications.
            Project::query()->whereKey($project->id)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($project->id);
            self::assertEnabled($project);
            $lockedDependency = ProjectTaskDependency::query()
                ->where('project_id', $project->id)
                ->where('id', $dependency->id)
                ->where(function ($query) use ($taskId) {
                    $query->where('predecessor_task_id', $taskId)
                        ->orWhere('successor_task_id', $taskId);
                })
                ->lockForUpdate()
                ->first();
            if (!$lockedDependency) {
                return [false, null, null];
            }

            // The caller may have lost project or task edit rights while waiting on the lock.
            $predecessor = ProjectTask::userTask($lockedDependency->predecessor_task_id, null);
            $successor = ProjectTask::userTask($lockedDependency->successor_task_id, null);
            if ((int)$predecessor->project_id !== (int)$project->id || (int)$successor->project_id !== (int)$project->id) {
                throw new ApiException('依赖不存在');
            }
            self::assertTasksVisible((int)$project->id, [$taskId, (int)$predecessor->id, (int)$successor->id]);
            ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $predecessor);
            ProjectPermission::userTaskPermission($project, ProjectPermission::TASK_UPDATE, $successor);

            $lockedDependency->delete();
            $predecessor->addLog('移除后置依赖', [], User::userid());
            $successor->addLog('移除前置依赖', [], User::userid());
            return [true, $predecessor, $successor];
        });
        if (!$deleted) {
            throw new ApiException('依赖不存在');
        }

        // Publish only after the edge and both audit logs have committed.
        self::publishRelationUpdate($predecessor);
        self::publishRelationUpdate($successor);
        self::notifyTask($predecessor, '任务依赖关系已更新');
        self::notifyTask($successor, '任务依赖关系已更新');
    }

    /**
     * Keep dependency edges consistent when a main task and its subtree move projects.
     * Edges entirely inside the moved set follow it; boundary edges are removed and audited.
     *
     * @param array<int> $taskIds
     */
    public static function handleProjectMove(int $sourceProjectId, int $targetProjectId, array $taskIds): void
    {
        $taskIds = array_values(array_unique(array_filter(array_map('intval', $taskIds), fn ($id) => $id > 0)));
        if (!$taskIds || $sourceProjectId === $targetProjectId) {
            return;
        }

        // Serialize this cleanup with dependency edits in the source project.
        Project::query()->whereKey($sourceProjectId)->lockForUpdate()->firstOrFail();
        $moving = array_fill_keys($taskIds, true);
        $edgesById = [];
        foreach (array_chunk($taskIds, 1000) as $taskIdBatch) {
            $edges = ProjectTaskDependency::query()
                ->where('project_id', $sourceProjectId)
                ->where(function ($query) use ($taskIdBatch) {
                    $query->whereIn('predecessor_task_id', $taskIdBatch)
                        ->orWhereIn('successor_task_id', $taskIdBatch);
                })
                ->orderBy('id')
                ->lockForUpdate()
                ->get();
            foreach ($edges as $edge) {
                $edgesById[(int)$edge->id] = $edge;
            }
        }

        $affectedTaskIds = [];
        foreach ($edgesById as $edge) {
            $predecessorId = (int)$edge->predecessor_task_id;
            $successorId = (int)$edge->successor_task_id;
            if (isset($moving[$predecessorId], $moving[$successorId])) {
                $edge->project_id = $targetProjectId;
                $edge->save();
                continue;
            }

            $edge->delete();
            $predecessor = ProjectTask::withTrashed()->find($predecessorId);
            $successor = ProjectTask::withTrashed()->find($successorId);
            $predecessor?->addLog('移除后置依赖', [], User::userid());
            $successor?->addLog('移除前置依赖', [], User::userid());
            $affectedTaskIds[] = $predecessorId;
            $affectedTaskIds[] = $successorId;
        }

        $affectedTaskIds = array_values(array_unique($affectedTaskIds));
        if (!$affectedTaskIds) {
            return;
        }

        DB::afterCommit(function () use ($affectedTaskIds) {
            foreach (array_chunk($affectedTaskIds, 1000) as $taskIdBatch) {
                $tasks = ProjectTask::withTrashed()->whereIn('id', $taskIdBatch)->get()->keyBy('id');
                foreach ($taskIdBatch as $taskId) {
                    $task = $tasks->get($taskId);
                    if (!$task) {
                        continue;
                    }
                    self::publishRelationUpdate($task);
                    self::notifyTask($task, '任务依赖关系已更新');
                }
            }
        });
    }

    /**
     * Return true if adding predecessor -> successor closes a directed cycle.
     */
    private static function wouldCreateCycle(int $projectId, int $predecessorId, int $successorId): bool
    {
        $pending = [$successorId];
        $visited = [];
        while ($pending) {
            $frontier = [];
            foreach ($pending as $taskId) {
                if (!isset($visited[$taskId])) {
                    $visited[$taskId] = true;
                    $frontier[] = $taskId;
                }
            }
            $pending = [];
            foreach (array_chunk($frontier, 1000) as $predecessorIds) {
                $successorIds = ProjectTaskDependency::query()
                    ->where('project_id', $projectId)
                    ->whereIn('predecessor_task_id', $predecessorIds)
                    ->distinct()
                    ->pluck('successor_task_id')
                    ->map(fn ($id) => (int)$id)
                    ->all();
                if (in_array($predecessorId, $successorIds, true)) {
                    return true;
                }
                foreach ($successorIds as $nextTaskId) {
                    if (!isset($visited[$nextTaskId])) {
                        $pending[] = $nextTaskId;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Load visible tasks in one query using the same visibility rules as project/task/one.
     * The owner view may additionally read project-wide tasks with visibility=1.
     *
     * @param array<int> $taskIds
     * @param array<string> $with
     * @return array<int, ProjectTask>
     */
    private static function visibleTasks(int $projectId, array $taskIds, array $with = []): array
    {
        $taskIds = array_values(array_unique(array_filter(array_map('intval', $taskIds), fn ($id) => $id > 0)));
        if (!$taskIds) {
            return [];
        }

        $userid = User::userid();
        return ProjectTask::with($with)
            ->allData()
            ->where('project_tasks.project_id', $projectId)
            ->whereIn('project_tasks.id', $taskIds)
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
                // Keep dependency authorization in sync with task__lists, where
                // assignees and explicitly visible users inherit descendant access.
                for ($depth = 1; $depth <= 5; $depth++) {
                    $query->orWhereExists(function ($ancestorQuery) use ($userid, $depth) {
                        $parentAlias = 'dependency_task_ancestor_1';
                        $ancestorQuery->selectRaw('1')->from('project_tasks as ' . $parentAlias);
                        for ($level = 2; $level <= $depth; $level++) {
                            $nextParentAlias = 'dependency_task_ancestor_' . $level;
                            $ancestorQuery->join('project_tasks as ' . $nextParentAlias, $nextParentAlias . '.id', '=', $parentAlias . '.parent_id');
                            $parentAlias = $nextParentAlias;
                        }
                        $ancestorQuery->whereColumn('dependency_task_ancestor_1.id', 'project_tasks.parent_id');
                        for ($level = 1; $level <= $depth; $level++) {
                            $ancestorAlias = 'dependency_task_ancestor_' . $level;
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
            })
            ->get()
            ->keyBy(fn ($task) => (int)$task->id)
            ->all();
    }

    /**
     * Apply the task visibility query in bounded batches for database drivers
     * with lower limits on the number of bound parameters in a whereIn clause.
     *
     * @param array<int> $taskIds
     * @param array<string> $with
     * @return array<int, ProjectTask>
     */
    private static function visibleTasksChunked(int $projectId, array $taskIds, array $with = []): array
    {
        $visibleTasks = [];
        $taskIds = array_values(array_unique(array_filter(array_map('intval', $taskIds), fn ($id) => $id > 0)));
        foreach (array_chunk($taskIds, 1000) as $taskIdChunk) {
            $visibleTasks += self::visibleTasks($projectId, $taskIdChunk, $with);
        }
        return $visibleTasks;
    }

    /** @param array<int> $taskIds */
    private static function assertTasksVisible(int $projectId, array $taskIds): void
    {
        $visible = self::visibleTasks($projectId, $taskIds);
        foreach ($taskIds as $taskId) {
            if (!isset($visible[(int)$taskId])) {
                throw new ApiException('无任务权限', ['task_id' => (int)$taskId], -4002);
            }
        }
    }

    private static function publishRelationUpdate(ProjectTask $task): void
    {
        try {
            $task->pushMsg('relation', null, null, false);
        } catch (Throwable $e) {
            // A failed websocket push must not make a committed relation look unsaved to the caller.
            report($e);
        }
    }

    private static function isEnabled(int $projectId): bool
    {
        return Project::query()->whereKey($projectId)->value('task_dependency') !== 'close';
    }

    private static function assertEnabled(Project $project): void
    {
        if (!self::isEnabled((int)$project->id)) {
            throw new ApiException('本项目已关闭任务依赖功能');
        }
    }

    /** Send a generic notice only to the task's own discussion, never the project-wide chat. */
    private static function notifyTask(ProjectTask $task, string $notice): void
    {
        if ((int)$task->dialog_id <= 0) {
            return;
        }
        try {
            WebSocketDialogMsg::sendMsg(null, (int)$task->dialog_id, 'notice', [
                'notice' => $notice,
            ]);
        } catch (Throwable $e) {
            report($e);
        }
    }
}
