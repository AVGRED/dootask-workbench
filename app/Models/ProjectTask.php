<?php

namespace App\Models;

use App\Module\ProjectTaskHandoffRecord;
use App\Module\ProjectTaskDependencyService;
use App\Module\ProjectTaskWorkService;
use App\Module\Timer;
use DB;
use Arr;
use Request;
use Carbon\Carbon;
use App\Module\Base;
use App\Tasks\PushTask;
use App\Exceptions\ApiException;
use App\Observers\ProjectTaskObserver;
use Hhxsv5\LaravelS\Swoole\Task\Task;
use Illuminate\Database\Eloquent\SoftDeletes;

/**
 * App\Models\ProjectTask
 *
 * @property int $id
 * @property int|null $parent_id 父级任务ID
 * @property int|null $project_id 项目ID
 * @property int|null $milestone_id 关联里程碑ID
 * @property int|null $column_id 列表ID
 * @property int|null $source_template_id 来源任务模板ID
 * @property int|null $source_template_version 来源任务模板版本
 * @property string|null $source_template_name 来源任务模板名称快照
 * @property int|null $dialog_id 聊天会话ID
 * @property int|null $flow_item_id 工作流状态ID
 * @property string|null $flow_item_name 工作流状态名称
 * @property string|null $name 标题
 * @property string|null $color 颜色
 * @property string|null $desc 描述
 * @property \Illuminate\Support\Carbon|null $start_at 计划开始时间
 * @property \Illuminate\Support\Carbon|null $end_at 计划结束时间
 * @property \Illuminate\Support\Carbon|null $archived_at 归档时间
 * @property int|null $archived_userid 归档会员
 * @property int|null $archived_follow 跟随项目归档（项目取消归档时任务也取消归档）
 * @property \Illuminate\Support\Carbon|null $complete_at 完成时间
 * @property int|null $userid 创建人
 * @property int|null $visibility 任务可见性：1-项目人员 2-任务人员 3-指定成员
 * @property int|null $p_level 优先级
 * @property string|null $p_name 优先级名称
 * @property string|null $p_color 优先级颜色
 * @property int|null $sort 排序(ASC)
 * @property string|null $loop 重复周期
 * @property \Illuminate\Support\Carbon|null $loop_at 下一次重复时间
 * @property \Illuminate\Support\Carbon|null $created_at
 * @property \Illuminate\Support\Carbon|null $updated_at
 * @property \Illuminate\Support\Carbon|null $deleted_at
 * @property int|null $deleted_userid 删除会员
 * @property-read \App\Models\ProjectTaskContent|null $content
 * @property-read int $file_num
 * @property-read int $msg_num
 * @property-read bool $overdue
 * @property-read int $percent
 * @property-read int $sub_complete
 * @property-read int $sub_num
 * @property-read bool $today
 * @property-read \App\Models\Project|null $project
 * @property-read \App\Models\ProjectColumn|null $projectColumn
 * @property-read \Illuminate\Database\Eloquent\Collection<int, \App\Models\ProjectTaskFile> $taskFile
 * @property-read int|null $task_file_count
 * @property-read \Illuminate\Database\Eloquent\Collection<int, \App\Models\ProjectTaskTag> $taskTag
 * @property-read int|null $task_tag_count
 * @property-read \Illuminate\Database\Eloquent\Collection<int, \App\Models\ProjectTaskUser> $taskUser
 * @property-read int|null $task_user_count
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask allData($userid = null)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask authData($userid = null, $owner = null)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask betweenTime($start, $end, $type = 'taskTime')
 * @method static \Illuminate\Database\Eloquent\Builder|AbstractModel cancelAppend()
 * @method static \Illuminate\Database\Eloquent\Builder|AbstractModel cancelHidden()
 * @method static \Illuminate\Database\Eloquent\Builder|AbstractModel change($array)
 * @method static \Illuminate\Database\Eloquent\Builder|AbstractModel getKeyValue()
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask newModelQuery()
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask newQuery()
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask onlyTrashed()
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask query()
 * @method static \Illuminate\Database\Eloquent\Builder|AbstractModel remove()
 * @method static \Illuminate\Database\Eloquent\Builder|AbstractModel saveOrIgnore()
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask searchByKeyword(string $keyword)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereArchivedAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereArchivedFollow($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereArchivedUserid($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereColor($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereColumnId($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereCompleteAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereCreatedAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereDeletedAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereDeletedUserid($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereDesc($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereDialogId($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereEndAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereFlowItemId($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereFlowItemName($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereId($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereLoop($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereLoopAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereName($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask wherePColor($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask wherePLevel($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask wherePName($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereParentId($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereProjectId($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereSort($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereStartAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereUpdatedAt($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereUserid($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask whereVisibility($value)
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask withTrashed()
 * @method static \Illuminate\Database\Eloquent\Builder|ProjectTask withoutTrashed()
 * @mixin \Eloquent
 */
class ProjectTask extends AbstractModel
{
    use SoftDeletes;

    protected $appends = [
        'file_num',
        'msg_num',
        'sub_num',
        'sub_complete',
        'percent',
        'today',
        'overdue',
    ];

    /**
     * 附件数量
     * @return int
     */
    public function getFileNumAttribute()
    {
        if (!isset($this->appendattrs['file_num'])) {
            $this->appendattrs['file_num'] = $this->parent_id > 0 ? 0 : ProjectTaskFile::whereTaskId($this->id)->count();
        }
        return $this->appendattrs['file_num'];
    }

    /**
     * 消息数量
     * @return int
     */
    public function getMsgNumAttribute()
    {
        if (!isset($this->appendattrs['msg_num'])) {
            $this->appendattrs['msg_num'] = $this->dialog_id ? WebSocketDialogMsg::whereDialogId($this->dialog_id)->count() : 0;
        }
        return $this->appendattrs['msg_num'];
    }

    /**
     * 生成子任务数据
     */
    private function generateSubTaskData()
    {
        if ($this->parent_id > 0) {
            $this->appendattrs['sub_num'] = 0;
            $this->appendattrs['sub_complete'] = 0;
            $this->appendattrs['percent'] = $this->complete_at ? 100 : 0;
            return;
        }
        if (!isset($this->appendattrs['sub_num'])) {
            $builder = self::whereParentId($this->id);
            $this->appendattrs['sub_num'] = $builder->count();
            $this->appendattrs['sub_complete'] = $builder->whereNotNull('complete_at')->count();
            //
            if ($this->complete_at) {
                $this->appendattrs['percent'] = 100;
            } elseif ($this->appendattrs['sub_complete'] == 0) {
                $this->appendattrs['percent'] = 0;
            } else {
                $this->appendattrs['percent'] = intval($this->appendattrs['sub_complete'] / $this->appendattrs['sub_num'] * 100);
            }
        }
    }

    /**
     * 子任务数量
     * @return int
     */
    public function getSubNumAttribute()
    {
        $this->generateSubTaskData();
        return $this->appendattrs['sub_num'];
    }

    /**
     * 子任务已完成数量
     * @return int
     */
    public function getSubCompleteAttribute()
    {
        $this->generateSubTaskData();
        return $this->appendattrs['sub_complete'];
    }

    /**
     * 进度（0-100）
     * @return int
     */
    public function getPercentAttribute()
    {
        $this->generateSubTaskData();
        return $this->appendattrs['percent'];
    }

    /**
     * 是否今日任务
     * @return bool
     */
    public function getTodayAttribute()
    {
        if ($this->end_at) {
            $end_at = Carbon::parse($this->end_at);
            if ($end_at->toDateString() == Carbon::now()->toDateString()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否过期
     * @return bool
     */
    public function getOverdueAttribute()
    {
        if ($this->end_at) {
            if (Carbon::parse($this->end_at)->lt(Carbon::now())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return \Illuminate\Database\Eloquent\Relations\HasOne
     */
    public function project(): \Illuminate\Database\Eloquent\Relations\HasOne
    {
        return $this->hasOne(Project::class, 'id', 'project_id');
    }

    /**
     * @return \Illuminate\Database\Eloquent\Relations\HasOne
     */
    public function projectColumn(): \Illuminate\Database\Eloquent\Relations\HasOne
    {
        return $this->hasOne(ProjectColumn::class, 'id', 'column_id');
    }

    /**
     * @return \Illuminate\Database\Eloquent\Relations\HasOne
     */
    public function content(): \Illuminate\Database\Eloquent\Relations\HasOne
    {
        return $this->hasOne(ProjectTaskContent::class, 'task_id', 'id')->orderByDesc('id');
    }

    /**
     * @return \Illuminate\Database\Eloquent\Relations\HasMany
     */
    public function taskFile(): \Illuminate\Database\Eloquent\Relations\HasMany
    {
        return $this->hasMany(ProjectTaskFile::class, 'task_id', 'id')->orderByDesc('id')->limit(50);
    }

    /**
     * @return \Illuminate\Database\Eloquent\Relations\HasMany
     */
    public function taskUser(): \Illuminate\Database\Eloquent\Relations\HasMany
    {
        return $this->hasMany(ProjectTaskUser::class, 'task_id', 'id')->orderByDesc('owner')->orderByDesc('id');
    }

    /**
     * @return \Illuminate\Database\Eloquent\Relations\HasMany
     */
    public function taskTag(): \Illuminate\Database\Eloquent\Relations\HasMany
    {
        return $this->hasMany(ProjectTaskTag::class, 'task_id', 'id')->orderBy('id');
    }

    /**
     * 查询所有任务（与正常查询多返回owner字段）
     * @param self $query
     * @param null $userid
     * @return self
     */
    public function scopeAllData($query, $userid = null)
    {
        $userid = $userid ?: User::userid();
        $query
            ->select([
                'project_tasks.*',
                'project_task_users.owner'
            ])
            ->leftJoin('project_task_users', function ($leftJoin) use ($userid) {
                $leftJoin
                    ->on('project_task_users.userid', '=', DB::raw($userid))
                    ->on('project_tasks.id', '=', 'project_task_users.task_id');
            });
        return $query;
    }

    /**
     * 查询自己负责或参与的任务
     * @param self $query
     * @param null $userid
     * @param null $owner
     * @return self
     */
    public function scopeAuthData($query, $userid = null, $owner = null)
    {
        $userid = $userid ?: User::userid();
        $query
            ->select([
                'project_tasks.*',
                'project_task_users.owner'
            ])
            ->selectRaw("1 AS assist")
            ->join('project_task_users', 'project_tasks.id', '=', 'project_task_users.task_id')
            ->where('project_task_users.userid', $userid);
        if ($owner !== null) {
            $query->where('project_task_users.owner', $owner);
        }
        return $query;
    }

    /**
     * 指定范围内的任务
     * @param $query
     * @param $start
     * @param $end
     * @param $type
     * @return mixed
     */
    public function scopeBetweenTime($query, $start, $end, $type = 'taskTime')
    {
        $query->where(function ($q1) use ($start, $end, $type) {
            switch ($type) {
                case 'createdTime':
                    $q1->where('project_tasks.created_at', '>=', $start)->where('project_tasks.created_at', '<=', $end);
                    break;

                default:
                    $q1->where(function ($q2) use ($start) {
                        $q2->where('project_tasks.start_at', '<=', $start)->where('project_tasks.end_at', '>=', $start);
                    })->orWhere(function ($q2) use ($end) {
                        $q2->where('project_tasks.start_at', '<=', $end)->where('project_tasks.end_at', '>=', $end);
                    })->orWhere(function ($q2) use ($start, $end) {
                        $q2->where('project_tasks.start_at', '>', $start)->where('project_tasks.end_at', '<', $end);
                    });
                    break;
            }
        });
        return $query;
    }

    /**
     * 按关键词搜索任务（Scope）
     * 支持：任务ID（纯数字）、任务名称、描述
     *
     * @param \Illuminate\Database\Eloquent\Builder $query
     * @param string $keyword 搜索关键词
     * @return \Illuminate\Database\Eloquent\Builder
     */
    public function scopeSearchByKeyword($query, string $keyword)
    {
        if (is_numeric($keyword)) {
            // 纯数字：匹配任务ID 或 名称/描述
            return $query->where(function ($q) use ($keyword) {
                $q->where("project_tasks.id", intval($keyword))
                    ->orWhere("project_tasks.name", "like", "%{$keyword}%")
                    ->orWhere("project_tasks.desc", "like", "%{$keyword}%");
            });
        }

        // 普通文本：搜索名称/描述
        return $query->where(function ($q) use ($keyword) {
            $q->where("project_tasks.name", "like", "%{$keyword}%")
                ->orWhere("project_tasks.desc", "like", "%{$keyword}%");
        });
    }

    /**
     * 生成描述
     * @param $content
     * @return string
     */
    public static function generateDesc($content)
    {
        $content = preg_replace_callback('/<ul class="tox-checklist">(.+?)<\/ul>/is', function ($matches) {
            return preg_replace_callback('/<li([^>]*)>(.+?)<\/li>/is', function ($m) {
                if (str_contains($m[1], 'tox-checklist--checked')) {
                    return "<li{$m[1]}>[√]{$m[2]} </li>";
                } else {
                    return "<li{$m[1]}>[ ]{$m[2]} </li>";
                }
            }, $matches[0]);
        }, $content);
        return Base::cutStr(strip_tags($content), 100);
    }

    /**
     * 标准化时间参数，兼容 start_at/end_at 转换为 times
     * @param array $data 请求数据
     * @param self|null $task 任务实例（更新时传入）
     * @return array 处理后的data
     */
    public static function normalizeTimes(array $data, ?self $task = null): array
    {
        if (isset($data['times']) || (!isset($data['start_at']) && !isset($data['end_at']))) {
            return $data;
        }

        $startAt = $data['start_at'] ?? null;
        $endAt = $data['end_at'] ?? null;

        if ($endAt && !$startAt) {
            // 只传 end_at：保留已有 start_at，否则取当前时间
            $startAt = $task?->start_at
                ? Carbon::parse($task->start_at)->toDateTimeString()
                : date('Y-m-d H:i:s');
        } elseif ($startAt && !$endAt) {
            // 只传 start_at：必须已有 end_at
            if (!$task?->end_at) {
                throw new ApiException('请设置结束时间');
            }
            $endAt = Carbon::parse($task->end_at)->toDateTimeString();
        }

        $data['times'] = [$startAt, $endAt];
        return $data;
    }

    /**
     * 校验用户未完成任务上限。调用方应在事务内执行。
     * @param array $userids
     * @param int|null $excludeTaskId 更新当前任务时排除自身
     * @return void
     */
    public static function assertUserTaskLimit(array $userids, ?int $excludeTaskId = null): void
    {
        $userids = array_values(array_unique(array_filter(array_map('intval', $userids), fn($userid) => $userid > 0)));
        if (!$userids) {
            return;
        }

        $incomingTaskCounts = array_fill_keys($userids, 1);
        self::assertUserTaskBatchLimit($incomingTaskCounts, $excludeTaskId === null ? [] : [$excludeTaskId]);
    }

    /**
     * 校验一批未完成任务恢复或分配后的个人任务上限。
     * @param array<int, int> $incomingTaskCounts userid => 新增的未完成任务数
     * @param array<int> $excludeTaskIds
     * @return void
     */
    public static function assertUserTaskBatchLimit(array $incomingTaskCounts, array $excludeTaskIds = []): void
    {
        $normalizedCounts = [];
        foreach ($incomingTaskCounts as $userid => $count) {
            $userid = (int)$userid;
            $count = (int)$count;
            if ($userid > 0 && $count > 0) {
                $normalizedCounts[$userid] = ($normalizedCounts[$userid] ?? 0) + $count;
            }
        }
        $userids = array_keys($normalizedCounts);
        sort($userids, SORT_NUMERIC);
        if (!$userids) {
            return;
        }

        $limit = intval(Base::settingFind('system', 'task_user_limit'));
        // 按用户 ID 固定顺序加锁，避免跨项目并发创建同时通过个人上限校验。
        User::query()->whereIn('userid', $userids)->orderBy('userid')->lockForUpdate()->get();
        foreach ($userids as $userid) {
            $query = ProjectTask::authData($userid)
                ->whereNull('project_tasks.complete_at')
                ->whereNull('project_tasks.archived_at');
            if ($excludeTaskIds) {
                $query->whereNotIn('project_tasks.id', $excludeTaskIds);
            }
            if ($query->count() + $normalizedCounts[$userid] > $limit) {
                throw new ApiException(User::userid2nickname($userid) . '负责或参与的未完成任务最多不能超过' . $limit . '个');
            }
        }
    }

    /**
     * 校验项目和列表的未完成任务容量。调用方应在事务内执行。
     * @param int $projectId
     * @param int $columnId
     * @param int|null $excludeTaskId 更新或移动任务时排除自身
     * @return void
     */
    public static function assertTaskPlacementLimit(int $projectId, int $columnId, ?int $excludeTaskId = null): void
    {
        self::assertTaskBatchPlacementLimit($projectId, $columnId, $excludeTaskId === null ? [] : [$excludeTaskId], 1);
    }

    /**
     * 校验一批任务进入目标项目和列表后的未完成任务容量。
     * 调用方应在事务内执行；excludeTaskIds 中的任务会从目标现有数量里扣除。
     * @param int $projectId
     * @param int $columnId
     * @param array $excludeTaskIds
     * @param int $incomingOpenTaskCount
     * @return void
     */
    public static function assertTaskBatchPlacementLimit(
        int $projectId,
        int $columnId,
        array $excludeTaskIds,
        int $incomingOpenTaskCount,
        ?array $columnExcludeTaskIds = null,
        ?int $incomingOpenColumnTaskCount = null
    ): void
    {
        Project::query()->whereKey($projectId)->lockForUpdate()->first();
        $columnExcludeTaskIds ??= $excludeTaskIds;
        $incomingOpenColumnTaskCount ??= $incomingOpenTaskCount;
        if ($incomingOpenTaskCount <= 0 && $incomingOpenColumnTaskCount <= 0) {
            return;
        }

        $projectTasks = ProjectTask::whereProjectId($projectId)
            ->whereNull('project_tasks.complete_at')
            ->whereNull('project_tasks.archived_at');
        $columnTasks = ProjectTask::whereColumnId($columnId)
            ->whereNull('project_tasks.complete_at')
            ->whereNull('project_tasks.archived_at');
        if ($excludeTaskIds && $incomingOpenTaskCount > 0) {
            $projectTasks->whereNotIn('project_tasks.id', $excludeTaskIds);
        }
        if ($columnExcludeTaskIds && $incomingOpenColumnTaskCount > 0) {
            $columnTasks->whereNotIn('project_tasks.id', $columnExcludeTaskIds);
        }
        if ($incomingOpenTaskCount > 0 && $projectTasks->count() + $incomingOpenTaskCount > 2000) {
            throw new ApiException('项目内未完成任务最多不能超过2000个');
        }
        if ($incomingOpenColumnTaskCount > 0 && $columnTasks->count() + $incomingOpenColumnTaskCount > 500) {
            throw new ApiException('单个列表未完成任务最多不能超过500个');
        }
    }

    /**
     * 读取子任务树并校验最大嵌套深度。
     * @param int $rootTaskId
     * @param int|null $maxCount
     * @return \Illuminate\Support\Collection
     */
    private static function taskSubtreeDescendants(int $rootTaskId, ?int $maxCount = null, bool $withTrashed = false, bool $lockForUpdate = false, bool $activeOnly = false): \Illuminate\Support\Collection
    {
        $descendants = collect();
        $projectId = (int)self::withTrashed()->whereKey($rootTaskId)->value('project_id');
        if ($projectId <= 0) {
            return $descendants;
        }
        $parentIds = [$rootTaskId];
        $depth = 0;
        while ($parentIds) {
            $children = collect();
            foreach (array_chunk($parentIds, 1000) as $parentIdBatch) {
                $builder = self::where('project_id', $projectId)->whereIn('parent_id', $parentIdBatch);
                if ($activeOnly) {
                    $builder->whereNull('complete_at')->whereNull('archived_at');
                }
                if ($withTrashed) {
                    $builder->withTrashed();
                }
                if ($lockForUpdate) {
                    $builder->lockForUpdate();
                }
                $children = $children->concat($builder->get(['id', 'parent_id', 'project_id', 'column_id', 'complete_at', 'archived_at', 'deleted_at']));
            }
            if ($children->isEmpty()) {
                break;
            }
            if (++$depth > 5) {
                throw new ApiException('子任务格式错误');
            }
            $descendants = $descendants->concat($children);
            if ($maxCount !== null && $descendants->count() > $maxCount) {
                throw new ApiException('子任务格式错误，最多可添加50项');
            }
            $parentIds = $children->pluck('id')->map(fn($id) => (int)$id)->all();
        }
        return $descendants;
    }

    /**
     * 获取当前任务下的所有后代子任务。
     * @return \Illuminate\Support\Collection
     */
    public function subTaskDescendants(): \Illuminate\Support\Collection
    {
        return self::taskSubtreeDescendants($this->id);
    }

    /**
     * 将父任务时间边界递归同步到所有层级的子任务。
     * @param array $oldAt 父任务修改前的开始和结束时间
     * @param array $updateMarking
     * @return void
     */
    private function syncSubTaskTimeBounds(array $oldAt, array &$updateMarking): void
    {
        $newAt = [Carbon::parse($this->start_at), Carbon::parse($this->end_at)];
        self::where('project_id', $this->project_id)->whereParentId($this->id)->chunk(100, function ($list) use ($oldAt, $newAt, &$updateMarking) {
            /** @var self $subTask */
            foreach ($list as $subTask) {
                $subTaskOldAt = [Carbon::parse($subTask->start_at), Carbon::parse($subTask->end_at)];
                $isUp = false;
                if (empty($subTask->start_at) || $subTaskOldAt[0]->eq($oldAt[0]) || $subTaskOldAt[0]->lt($newAt[0])) {
                    $subTask->start_at = $this->start_at;
                    $isUp = true;
                }
                if (empty($subTask->end_at) || $subTaskOldAt[1]->eq($oldAt[1]) || $subTaskOldAt[1]->gt($newAt[1])) {
                    $subTask->end_at = $this->end_at;
                    $isUp = true;
                }
                if ($subTask->start_at && Carbon::parse($subTask->start_at)->gt($subTask->end_at)) {
                    $subTask->start_at = $this->start_at;
                    $isUp = true;
                }
                if ($isUp) {
                    $updateMarking['is_update_subtask'] = true;
                    $subTask->addLog("同步修改{任务}时间");
                    $subTask->save();
                    $subTask->syncSubTaskTimeBounds($subTaskOldAt, $updateMarking);
                }
            }
        });
    }

    /**
     * 校验在指定任务下新增子任务是否仍处于支持的层级和数量范围内。
     * @param self $parentTask
     * @return void
     */
    public static function assertSubtaskCanBeAdded(self $parentTask): void
    {
        $parentDepth = 0;
        $rootTask = $parentTask;
        while ($rootTask->parent_id > 0) {
            $parentDepth++;
            $rootTask = self::where('project_id', $parentTask->project_id)->find($rootTask->parent_id);
            if (!$rootTask || $parentDepth >= 5) {
                throw new ApiException('子任务格式错误');
            }
        }
        // Every branch under one main task uses the root row as its shared
        // serialization point. Locking only the immediate parent lets parallel
        // additions to different branches both pass the same 50-task check.
        $rootTask = self::where('project_id', $parentTask->project_id)->whereKey($rootTask->id)->lockForUpdate()->first();
        if (!$rootTask) {
            throw new ApiException('子任务格式错误');
        }
        // Use locking reads for descendants too, so InnoDB does not reuse an
        // older repeatable-read snapshot created while resolving the ancestors.
        $activeDescendants = self::taskSubtreeDescendants($rootTask->id, null, false, true, true);
        if ($activeDescendants->count() >= 50) {
            throw new ApiException('子任务格式错误，最多可添加50项');
        }
    }

    /**
     * 添加任务
     * @param $data
     * @return self
     */
    public static function addTask($data)
    {
        $parent_id  = intval($data['parent_id'] ?? 0);
        $project_id = intval($data['project_id'] ?? 0);
        $column_id  = intval($data['column_id'] ?? 0);
        $name       = $data['name'] ?? '';
        $content    = $data['content'] ?? '';
        $times      = $data['times'] ?? [];
        $owner      = $data['owner'] ?? [];
        $add_assist = intval($data['add_assist'] ?? 0);  // 将自己添加到参与者
        $assist     = $data['assist'] ?? [];              // 参与者，此项设置时 add_assist 无效
        $subtasks   = $data['subtasks'] ?? [];
        $p_level    = intval($data['p_level'] ?? 0);
        $p_name     = $data['p_name'] ?? '';
        $p_color    = $data['p_color'] ?? '';
        $top        = intval($data['top'] ?? 0);
        $userid     = User::userid();
        $visibility = $data['visibility_appoint'] ?? $data['visibility'] ?? 1;
        $visibility_userids = is_array($data['visibility_appointor'] ?? null) ? $data['visibility_appointor'] : [];
        $estimatedMinutes = $data['estimated_minutes'] ?? null;
        if ($estimatedMinutes !== null && $estimatedMinutes !== '') {
            $estimatedMinutes = filter_var($estimatedMinutes, FILTER_VALIDATE_INT);
            if ($estimatedMinutes === false || $estimatedMinutes < 0 || $estimatedMinutes > 1000000) {
                throw new ApiException('预估工时须为 0 至 1000000 分钟');
            }
        } else {
            $estimatedMinutes = null;
        }
        $templateSource = $parent_id === 0 && is_array($data['template_source'] ?? null) ? $data['template_source'] : [];
        $milestoneName = $data['milestone_name'] ?? '';
        if (!is_string($milestoneName)) {
            throw new ApiException('任务里程碑格式错误');
        }
        $milestoneName = trim($milestoneName);
        $milestoneId = null;
        if ($milestoneName !== '') {
            $milestones = ProjectMilestone::query()
                ->where('project_id', $project_id)
                ->where('name', $milestoneName)
                ->orderBy('sort')
                ->orderBy('id')
                ->limit(2)
                ->get();
            if ($milestones->count() > 1) {
                throw new ApiException('当前项目存在重名里程碑，请先重命名：' . $milestoneName);
            }
            $milestone = $milestones->first();
            if (!$milestone) {
                throw new ApiException('模板关联的里程碑在当前项目中不存在：' . $milestoneName);
            }
            $milestoneId = $milestone->id;
        }
        //
        $retPre = $parent_id ? '子任务' : '任务';

        // 优先级：主任务在缺省时按系统默认补齐，并尽量补全 name/color
        if ($parent_id == 0) {
            $priorityList = Setting::normalizeTaskPriorityList(Base::setting('priority'));
            if ($p_level > 0) {
                $matched = collect($priorityList)->first(fn($item) => intval($item['priority']) === $p_level);
            } else {
                $matched = Setting::getDefaultTaskPriorityItem($priorityList);
            }
            if ($matched) {
                $p_level = $p_level > 0 ? $p_level : intval($matched['priority']);
                $p_name = $p_name ?: $matched['name'];
                $p_color = $p_color ?: $matched['color'];
            }
        }

        $task = self::createInstance([
            'parent_id' => $parent_id,
            'project_id' => $project_id,
            'milestone_id' => $milestoneId,
            'estimated_minutes' => $parent_id === 0 && $estimatedMinutes ? $estimatedMinutes : null,
            'column_id' => $column_id,
            'p_level' => $p_level,
            'p_name' => $p_name,
            'p_color' => $p_color,
            'source_template_id' => $templateSource['id'] ?? null,
            'source_template_version' => $templateSource['version'] ?? null,
            'source_template_name' => $templateSource['name'] ?? null,
            'visibility' => $visibility ?: 1
        ]);
        if ($content) {
            $task->desc = self::generateDesc($content);
        }
        // 标题
        if (empty($name)) {
            throw new ApiException($retPre . '描述不能为空');
        } elseif (mb_strlen($name) > 255) {
            throw new ApiException($retPre . '描述最多只能设置255个字');
        }
        $task->name = $name;
        // 时间
        if ($times) {
            list($start, $end) = is_string($times) ? explode(",", $times) : (is_array($times) ? $times : []);
            if (Timer::isDate($start) && Timer::isDate($end) && $start != $end) {
                $task->start_at = Carbon::parse($start);
                $task->end_at = Carbon::parse($end);
            }
        }
        // 负责人
        $owner = is_array($owner) ? $owner : [$owner];
        $tmpArray = [];
        foreach ($owner as $uid) {
            if (intval($uid) == 0) continue;
            if (!ProjectUser::whereProjectId($project_id)->whereUserid($uid)->exists()) {
                throw new ApiException($retPre . '负责人填写错误');
            }
            $tmpArray[] = $uid;
        }
        $owner = $tmpArray;
        // 协助人员
        $assist = is_array($assist) ? $assist : [];
        if (empty($assist)) {
            // 添加自己
            if (!in_array($userid, $owner) && $add_assist) {
                $assist = [$userid];
            }
        }
        // 创建人
        $task->userid = $userid;
        // 排序位置
        if ($top) {
            $task->sort = intval(self::whereColumnId($task->column_id)->orderBy('sort')->value('sort')) - 1;
        } else {
            $task->sort = intval(self::whereColumnId($task->column_id)->orderByDesc('sort')->value('sort')) + 1;
        }
        // 工作流
        $projectFlow = ProjectFlow::whereProjectId($project_id)->orderByDesc('id')->first();
        if ($projectFlow) {
            $projectFlowItem = ProjectFlowItem::whereFlowId($projectFlow->id)->orderBy('sort')->get();
            // 赋一个开始状态
            foreach ($projectFlowItem as $item) {
                if ($item->status == 'start') {
                    $task->flow_item_id = $item->id;
                    $task->flow_item_name = $item->status . "|" . $item->name . "|" . $item->color;
                    $owner = array_merge($owner, $item->userids);
                    break;
                }
            }
        }
        //
        return AbstractModel::transaction(function () use ($assist, $times, $subtasks, $content, $owner, $task, $visibility_userids, $project_id, $column_id, $parent_id) {
            self::assertTaskPlacementLimit($project_id, $column_id);
            if ($parent_id > 0) {
                $parentTask = self::whereKey($parent_id)->lockForUpdate()->first();
                if (!$parentTask || intval($parentTask->project_id) !== $project_id) {
                    throw new ApiException('子任务格式错误');
                }
                self::assertSubtaskCanBeAdded($parentTask);
            }

            $owner = array_values(array_unique($owner));
            $assist = array_values(array_diff(array_unique($assist), $owner));
            $participantIds = array_values(array_unique(array_merge($owner, $assist)));
            self::assertUserTaskLimit($participantIds);

            $task->save();
            foreach ($owner as $uid) {
                ProjectTaskUser::createInstance([
                    'project_id' => $task->project_id,
                    'task_id' => $task->id,
                    'task_pid' => $task->parent_id ?: $task->id,
                    'userid' => $uid,
                    'owner' => 1,
                ])->save();
            }
            foreach ($assist as $uid) {
                ProjectTaskUser::createInstance([
                    'project_id' => $task->project_id,
                    'task_id' => $task->id,
                    'task_pid' => $task->parent_id ?: $task->id,
                    'userid' => $uid,
                    'owner' => 0,
                ])->save();
            }

            // 可见性
            foreach ($visibility_userids as $uid) {
                ProjectTaskVisibilityUser::createInstance([
                    'project_id' => $task->project_id,
                    'task_id' => $task->id,
                    'userid' => $uid
                ])->save();
            }

            if ($content) {
                ProjectTaskContent::createInstance([
                    'project_id' => $task->project_id,
                    'task_id' => $task->id,
                    'userid' => $task->userid,
                    'desc' => $task->desc,
                    'content' => [
                        'url' => ProjectTaskContent::saveContent($task->id, $content)
                    ],
                ])->save();
            }
            if ($subtasks && is_array($subtasks)) {
                foreach ($subtasks as $subtask) {
                    if (!is_array($subtask)) continue;
                    $subtask = array_merge([
                        'content' => '',
                        'times' => $times,
                        'owner' => [],
                        'add_assist' => 0,
                        'assist' => [],
                        'p_level' => $task->p_level,
                        'p_name' => $task->p_name,
                        'p_color' => $task->p_color,
                        'top' => 0,
                        'visibility' => $task->visibility,
                        'visibility_appointor' => $visibility_userids,
                        'subtasks' => [],
                    ], $subtask);
                    $subtaskTimes = $subtask['times'];
                    list($start, $end) = is_string($subtaskTimes) ? explode(",", $subtaskTimes) : (is_array($subtaskTimes) ? $subtaskTimes : []);
                    if (Timer::isDate($start) && Timer::isDate($end) && $start != $end) {
                        if ($task->start_at && Carbon::parse($start)->lt($task->start_at)) {
                            throw new ApiException('子任务开始时间不能小于主任务开始时间');
                        }
                        if ($task->end_at && Carbon::parse($end)->gt($task->end_at)) {
                            throw new ApiException('子任务结束时间不能大于主任务结束时间');
                        }
                    } else {
                        $subtask['times'] = $times;
                    }
                    $subtask['parent_id'] = $task->id;
                    $subtask['project_id'] = $task->project_id;
                    $subtask['column_id'] = $task->column_id;
                    self::addTask($subtask);
                }
            }
            $task->addLog("创建{任务}");
            ProjectTaskHandoffRecord::created($task);
            return $task;
        });
    }

    /**
     * 修改任务
     * @param $data
     * @param array $updateMarking    更新的标记
     * - is_update_project  是否更新项目数据（项目统计）
     * - is_update_content  是否更新任务详情
     * - is_update_maintask 是否更新主任务
     * - is_update_subtask  是否更新子任务
     * @return bool
     */
    public function updateTask($data, &$updateMarking = [])
    {
        //
        // Placement and reopen checks lock the project row as their shared
        // capacity serialization point. Acquire it before track() locks the
        // task row, matching addTask()'s project -> task lock order.
        $projectLockIds = Arr::exists($data, 'flow_item_id')
            || Arr::exists($data, 'complete_at')
            || Arr::exists($data, 'column_id')
            ? [(int)$this->project_id]
            : [];
        ProjectTaskHandoffRecord::track($this, isset($data['flow_item_id']) ? 'flow' : 'update', function () use ($data, &$updateMarking) {
            // 主任务
            $mainTask = $this->parent_id > 0
                ? self::where('project_id', $this->project_id)->find($this->parent_id)
                : null;
            // 工作流
            if (Arr::exists($data, 'flow_item_id')) {
                if ($this->flow_item_id == $data['flow_item_id']) {
                    throw new ApiException('任务状态未发生改变');
                }
                $flowData = [
                    'flow_item_id' => $this->flow_item_id,
                    'flow_item_name' => $this->flow_item_name,
                ];
                $currentFlowItem = null;
                $newFlowItem = ProjectFlowItem::whereProjectId($this->project_id)->find(intval($data['flow_item_id']));
                if (empty($newFlowItem) || empty($newFlowItem->projectFlow)) {
                    throw new ApiException('任务状态不存在');
                }
                if ($this->flow_item_id) {
                    // 判断符合流转
                    $currentFlowItem = ProjectFlowItem::find($this->flow_item_id);
                    if ($currentFlowItem) {
                        if (!in_array($newFlowItem->id, $currentFlowItem->turns)) {
                            throw new ApiException("当前状态[{$currentFlowItem->name}]不可流转到[{$newFlowItem->name}]");
                        }
                        if ($currentFlowItem->userlimit) {
                            $isProjectOwner = $this->useridInTheProject(User::userid()) === 2;
                            if (!$isProjectOwner && !in_array(User::userid(), $currentFlowItem->userids)) {
                                throw new ApiException("当前状态[{$currentFlowItem->name}]仅限状态负责人或项目负责人修改");
                            }
                        }
                    }
                }
                if ($newFlowItem->status == 'end') {
                    // 判断自动完成
                    if (!$this->complete_at) {
                        $flowData['complete_at'] = $this->complete_at;
                        $data['complete_at'] = date("Y-m-d H:i");
                    }
                } else {
                    // 判断自动打开
                    if ($this->complete_at) {
                        $flowData['complete_at'] = $this->complete_at;
                        $data['complete_at'] = false;
                    }
                }
                $flowUserids = $newFlowItem->userids;
                if ($flowUserids) {
                    // 确认负责人在任务中
                    $flowUserids = ProjectUser::whereProjectId($this->project_id)->whereIn('userid', $flowUserids)->pluck('userid')->toArray();
                }
                if ($flowUserids) {
                    // 判断自动添加负责人
                    $flowData['owner'] = $data['owner'] = $this->taskUser->where('owner', 1)->pluck('userid')->toArray();
                    if (in_array($newFlowItem->usertype, ["replace", "merge"])) {
                        // 流转模式、剔除模式
                        if ($this->parent_id === 0) {
                            $flowData['assist'] = $data['assist'] = $this->taskUser->where('owner', 0)->pluck('userid')->toArray();
                            $data['assist'] = array_merge($data['assist'], $data['owner']);
                        }
                        $data['owner'] = $flowUserids;
                        // 判断剔除模式：保留操作状态的人员
                        if ($newFlowItem->usertype == "merge") {
                            $data['owner'][] = User::userid();
                        }
                    } else {
                        // 添加模式
                        $data['owner'] = array_merge($data['owner'], $flowUserids);
                    }
                    $data['owner'] = array_values(array_unique($data['owner']));
                    if (isset($data['assist'])) {
                        $data['assist'] = array_values(array_unique(array_diff($data['assist'], $data['owner'])));
                    }
                }
                if ($newFlowItem->columnid && ProjectColumn::whereProjectId($this->project_id)->whereId($newFlowItem->columnid)->exists()) {
                    $data['column_id'] = $newFlowItem->columnid;
                }
                $this->flow_item_id = $newFlowItem->id;
                $this->flow_item_name = $newFlowItem->status . "|" . $newFlowItem->name . "|" . $newFlowItem->color;
                $this->addLog("修改{任务}状态", [
                    'flow' => $flowData,
                    'change' => [$currentFlowItem?->name, $newFlowItem->name]
                ]);
                ProjectTaskFlowChange::createInstance([
                    'task_id' => $this->id,
                    'userid' => User::userid(),
                    'before_flow_item_id' => $flowData['flow_item_id'],
                    'before_flow_item_name' => $flowData['flow_item_name'],
                    'after_flow_item_id' => $this->flow_item_id,
                    'after_flow_item_name' => $this->flow_item_name,
                ])->save();
            }
            // 状态
            if (Arr::exists($data, 'complete_at')) {
                // 子任务：主任务已完成时无法修改
                if ($mainTask?->complete_at) {
                    throw new ApiException('主任务已完成，无法修改子任务状态');
                }
                if (Timer::isDate($data['complete_at'])) {
                    // 标记已完成
                    if ($this->complete_at) {
                        throw new ApiException('任务已完成');
                    }
                    // 只有用户单独提交 complete_at 时才自动设置工作流状态
                    if (!Arr::exists($data, 'flow_item_id')) {
                        $flowItemName = $this->checkAndAutoSetFlowItem('end', -4005);
                    } else {
                        $flowItemName = isset($newFlowItem) ? $newFlowItem->name : null;
                    }
                    $this->completeTask(Carbon::now(), $flowItemName);
                } else {
                    // 标记未完成
                    if (!$this->complete_at) {
                        throw new ApiException('未完成任务');
                    }
                    self::assertTaskPlacementLimit(
                        $this->project_id,
                        intval($data['column_id'] ?? $this->column_id),
                        $this->id
                    );
                    $reopenOwnerIds = Arr::exists($data, 'owner')
                        ? (is_array($data['owner']) ? $data['owner'] : [$data['owner']])
                        : ProjectTaskUser::whereTaskId($this->id)->whereOwner(1)->pluck('userid')->toArray();
                    $reopenAssistIds = Arr::exists($data, 'assist')
                        ? (is_array($data['assist']) ? $data['assist'] : [$data['assist']])
                        : ProjectTaskUser::whereTaskId($this->id)->whereOwner(0)->pluck('userid')->toArray();
                    $reopenParticipantIds = array_values(array_unique(array_filter(array_map('intval', array_merge($reopenOwnerIds, $reopenAssistIds)), fn($userid) => $userid > 0)));
                    $reopenParticipantIds = ProjectUser::whereProjectId($this->project_id)
                        ->whereIn('userid', $reopenParticipantIds)
                        ->pluck('userid')
                        ->map(fn($userid) => (int)$userid)
                        ->toArray();
                    self::assertUserTaskLimit($reopenParticipantIds, $this->id);
                    // 只有用户单独提交 complete_at 时才自动设置工作流状态
                    if (!Arr::exists($data, 'flow_item_id')) {
                        $this->checkAndAutoSetFlowItem('start', -4006);
                    }
                    $this->completeTask(null);
                }
                $updateMarking['is_update_project'] = true;
            }
            if (Arr::exists($data, 'owner') || Arr::exists($data, 'assist')) {
                $currentOwnerIds = ProjectTaskUser::whereTaskId($this->id)->whereOwner(1)->pluck('userid')->toArray();
                $currentAssistIds = ProjectTaskUser::whereTaskId($this->id)->whereOwner(0)->pluck('userid')->toArray();
                $currentUserIds = ProjectTaskUser::whereTaskId($this->id)->pluck('userid')->toArray();
                $targetOwnerIds = Arr::exists($data, 'owner')
                    ? (is_array($data['owner']) ? $data['owner'] : [$data['owner']])
                    : $currentOwnerIds;
                $targetAssistIds = Arr::exists($data, 'assist')
                    ? (is_array($data['assist']) ? $data['assist'] : [$data['assist']])
                    : $currentAssistIds;
                $targetUserIds = array_values(array_unique(array_filter(array_map('intval', array_merge($targetOwnerIds, $targetAssistIds)), fn($userid) => $userid > 0)));
                $targetUserIds = ProjectUser::whereProjectId($this->project_id)
                    ->whereIn('userid', $targetUserIds)
                    ->pluck('userid')
                    ->map(fn($userid) => (int)$userid)
                    ->toArray();
                $addedUserIds = array_values(array_diff($targetUserIds, array_map('intval', $currentUserIds)));
                if ($addedUserIds && !$this->complete_at && !$this->archived_at) {
                    self::assertUserTaskLimit($addedUserIds, $this->id);
                }
            }
            // 标题
            if (Arr::exists($data, 'name') && $this->name != $data['name']) {
                if (empty($data['name'])) {
                    throw new ApiException('任务描述不能为空');
                } elseif (mb_strlen($data['name']) > 255) {
                    throw new ApiException('任务描述最多只能设置255个字');
                }
                $this->addLog("修改{任务}标题", [
                    'change' => [$this->name, $data['name']]
                ]);
                $this->name = $data['name'];
                if ($this->dialog_id) {
                    WebSocketDialog::updateData(['id' => $this->dialog_id], ['name' => $this->name]);
                }
            }
            // 负责人
            if (Arr::exists($data, 'owner')) {
                $older = $this->taskUser->where('owner', 1)->pluck('userid')->toArray();
                $array = [];
                $owner = is_array($data['owner']) ? $data['owner'] : [$data['owner']];
                if (count($owner) > 10) {
                    throw new ApiException('任务负责人最多不能超过10个');
                }
                foreach ($owner as $uid) {
                    if (intval($uid) == 0) continue;
                    if (!$this->project->useridInTheProject($uid)) continue;
                    $row = ProjectTaskUser::where("task_id", $this->id)
                        ->where("userid", $uid)
                        ->where("owner", '!=', 2)
                        ->first();
                    if (empty($row)) {
                        ProjectTaskUser::createInstance([
                            'task_id' => $this->id,
                            'userid' => $uid,
                            'project_id' => $this->project_id,
                            'task_pid' => $this->parent_id ?: $this->id,
                            'owner' => 1,
                        ])->save();
                    } else {
                        $row->project_id = $this->project_id;
                        $row->task_pid = $this->parent_id ?: $this->id;
                        $row->owner = 1;
                        $row->save();
                    }
                    $array[] = $uid;
                }
                if ($array) {
                    if (count($older) == 0 && count($array) == 1 && $array[0] == User::userid()) {
                        $this->addLog("认领{任务}");
                    } else {
                        if (array_merge(array_diff($array, $older), array_diff($older, $array))) {
                            $this->addLog("修改{任务}负责人", ['userid' => $array]);
                        }
                    }
                    $this->taskPush(array_values(array_diff($array, $older)), 0);
                }
                $rows = ProjectTaskUser::whereTaskId($this->id)->whereOwner(1)->whereNotIn('userid', $array)->get();
                if ($rows->isNotEmpty()) {
                    $this->addLog("删除{任务}负责人", ['userid' => $rows->pluck('userid')]);
                    foreach ($rows as $row) {
                        $row->delete();
                    }
                }
                $updateMarking['is_update_project'] = true;
                $this->updated_at = Carbon::now();
                $this->syncDialogUser();
            }
            // 可见性
            if (Arr::exists($data, 'visibility') || Arr::exists($data, 'visibility_appointor')) {
                if (Arr::exists($data, 'visibility')) {
                    $this->visibility = $data["visibility"];
                    ProjectTask::whereParentId($data['task_id'])->change(['visibility' => $data["visibility"]]);
                }
                ProjectTaskVisibilityUser::whereTaskId($data['task_id'])->remove();
                if (Arr::exists($data, 'visibility_appointor')) {
                    foreach ($data['visibility_appointor'] as $uid) {
                        if ($uid) {
                            ProjectTaskVisibilityUser::createInstance([
                                'project_id' => $this->project_id,
                                'task_id' => $this->id,
                                'userid' => $uid
                            ])->save();
                        }
                    }
                    if (!Arr::exists($data, 'visibility')) {
                        ProjectTaskObserver::visibilityUpdate($this);
                    }
                }
            }
            // 计划时间（原则：子任务时间在主任务时间内）
            if (Arr::exists($data, 'times')) {
                $oldAt = [Carbon::parse($this->start_at), Carbon::parse($this->end_at)];
                $oldStringAt = $this->start_at ? ($oldAt[0]->toDateTimeString() . '~' . $oldAt[1]->toDateTimeString()) : '';
                $isOverdue = $this->overdue;
                $clearSubTaskTime = false;
                $this->start_at = null;
                $this->end_at = null;
                $times = $data['times'];
                list($start, $end, $desc) = is_string($times) ? explode(",", $times) : (is_array($times) ? $times : []);
                if (Timer::isDate($start) && Timer::isDate($end) && $start != $end) {
                    $start_at = Carbon::parse($start);
                    $end_at = Carbon::parse($end);
                    if ($this->parent_id > 0) {
                        // 判断同步主任务时间（子任务时间 超出 主任务）
                        if ($mainTask) {
                            $isUp = false;
                            if (empty($mainTask->start_at) || $start_at->lt(Carbon::parse($mainTask->start_at))) {
                                $mainTask->start_at = $start_at;
                                $isUp = true;
                            }
                            if (empty($mainTask->end_at) || $end_at->gt(Carbon::parse($mainTask->end_at))) {
                                $mainTask->end_at = $end_at;
                                $isUp = true;
                            }
                            if ($isUp) {
                                $updateMarking['is_update_maintask'] = true;
                                $mainTask->addLog("同步修改{任务}时间");
                                $mainTask->save();
                            }
                        }
                    }
                    $this->start_at = $start_at;
                    $this->end_at = $end_at;
                } else {
                    if ($this->parent_id > 0) {
                        // 清空子任务时间（子任务时间等于主任务时间）
                        $this->start_at = $mainTask->start_at;
                        $this->end_at = $mainTask->end_at;
                        $clearSubTaskTime = true;
                    }
                }
                // 保持每一层子任务的时间都落在其直接父任务的时间范围内。
                $this->syncSubTaskTimeBounds($oldAt, $updateMarking);
                $existAt = $this->start_at && !$clearSubTaskTime;
                $newStringAt = $existAt ? ($this->start_at->toDateTimeString() . '~' . $this->end_at->toDateTimeString()) : '';
                if ($isOverdue) {
                    $effectiveEndTime = $existAt ? Carbon::parse($this->end_at)->min(Carbon::now()) : Carbon::now();
                    $this->addLog("{任务}超期未完成", [
                        'cache' => [
                            'task_at' => $oldStringAt,
                            'change_at' => $newStringAt,
                            'over_sec' => (int)$effectiveEndTime->diffInSeconds($oldAt[1], true),
                            'owners' => $this->taskUser->where('owner', 1)->pluck('userid')->toArray(),
                            'assists' => $this->taskUser->where('owner', 0)->pluck('userid')->toArray(),
                        ]
                    ]);
                }
                $newDesc = $desc ? "（备注：{$desc}）" : "";
                $this->addLog("修改{任务}时间" . $newDesc, [
                    'change' => [$oldStringAt, $newStringAt]
                ]);
                $this->taskPush(null, 3, $newDesc);
            }
            // 以下仅顶级任务可修改
            if ($this->parent_id === 0) {
                // 重复周期
                $loopAt = $this->loop_at;
                $loopDesc = $this->loopDesc();
                if (Arr::exists($data, 'loop')) {
                    $this->loop = $data['loop'];
                    if (!$this->refreshLoop()) {
                        throw new ApiException('重复周期选择错误');
                    }
                } elseif (Arr::exists($data, 'times')) {
                    // 更新任务时间也要更新重复周期
                    $this->refreshLoop();
                }
                $oldLoop = $loopAt ? Carbon::parse($loopAt)->toDateTimeString() : null;
                $newLoop = $this->loop_at ? Carbon::parse($this->loop_at)->toDateTimeString() : null;
                if ($oldLoop != $newLoop) {
                    $this->addLog("修改{任务}下个周期", [
                        'change' => [$oldLoop, $newLoop]
                    ]);
                }
                if ($loopDesc != $this->loopDesc()) {
                    $this->addLog("修改{任务}重复周期", [
                        'change' => [$loopDesc, $this->loopDesc()]
                    ]);
                }
                // 协助人员
                if (Arr::exists($data, 'assist')) {
                    $older = $this->taskUser->where('owner', 0)->pluck('userid')->toArray();
                    $array = [];
                    $assist = is_array($data['assist']) ? $data['assist'] : [$data['assist']];
                    if (count($assist) > 10) {
                        throw new ApiException('任务协助人员最多不能超过10个');
                    }
                    foreach ($assist as $uid) {
                        if (intval($uid) == 0) continue;
                        if (!$this->project->useridInTheProject($uid)) continue;
                        //
                        ProjectTaskUser::updateInsert([
                            'task_id' => $this->id,
                            'userid' => $uid,
                        ], [
                            'project_id' => $this->project_id,
                            'task_pid' => $this->id,
                            'owner' => 0,
                        ]);
                        $array[] = $uid;
                    }
                    if ($array) {
                        if (array_merge(array_diff($array, $older), array_diff($older, $array))) {
                            $this->addLog("修改{任务}协助人员", ['userid' => $array]);
                        }
                    }
                    $rows = ProjectTaskUser::whereTaskId($this->id)->whereOwner(0)->whereNotIn('userid', $array)->get();
                    if ($rows->isNotEmpty()) {
                        $this->addLog("删除{任务}协助人员", ['userid' => $rows->pluck('userid')]);
                        foreach ($rows as $row) {
                            $row->delete();
                        }
                    }
                    $this->updated_at = Carbon::now();
                    $this->syncDialogUser();
                }
                // 背景色
                if (Arr::exists($data, 'color') && $this->color != $data['color']) {
                    $this->addLog("修改{任务}背景色", [
                        'change' => [$this->color, $data['color']]
                    ]);
                    $this->color = $data['color'];
                }
                // 列表
                if (Arr::exists($data, 'column_id')) {
                    $oldName = ProjectColumn::whereProjectId($this->project_id)->whereId($this->column_id)->value('name');
                    $column = ProjectColumn::whereProjectId($this->project_id)->whereId($data['column_id'])->first();
                    if (empty($column)) {
                        throw new ApiException('请选择正确的列表');
                    }
                    if (!$this->complete_at && !$this->archived_at) {
                        self::assertTaskPlacementLimit($this->project_id, $column->id, $this->id);
                    }
                    $this->addLog("修改{任务}列表", [
                        'change' => [$oldName, $column->name]
                    ]);
                    $this->column_id = $column->id;
                }
                // 内容
                if (Arr::exists($data, 'content')) {
                    $logRecord = [];
                    $logContent = ProjectTaskContent::whereTaskId($this->id)->orderByDesc('id')->first();
                    if ($logContent) {
                        $logRecord['link'] = [
                            'title' => '查看历史',
                            'url' => 'single/task/content/' . $this->id . '?history_id=' . $logContent->id,
                        ];
                    }
                    $this->desc = self::generateDesc($data['content']);
                    ProjectTaskContent::createInstance([
                        'project_id' => $this->project_id,
                        'task_id' => $this->id,
                        'userid' => User::userid(),
                        'desc' => $this->desc,
                        'content' => [
                            'url' => ProjectTaskContent::saveContent($this->id, $data['content'])
                        ],
                    ])->save();
                    $this->addLog("修改{任务}详细描述", $logRecord);
                    $updateMarking['is_update_content'] = true;
                }
                // 标签
                if (Arr::exists($data, 'task_tag')) {
                    $oldTags = collect($this->taskTag);
                    $newTags = collect($data['task_tag']);

                    // 找出需要删除的标签（在旧数据中存在，但在新数据中不存在）
                    $deletedTags = $oldTags->filter(function ($oldTag) use ($newTags) {
                        return !$newTags->contains('name', $oldTag['name']);
                    });
                    if ($deletedTags->isNotEmpty()) {
                        $this->addLog("删除{任务}标签", [
                            'tags' => $deletedTags->values()->all()
                        ]);
                        ProjectTaskTag::whereProjectId($this->project_id)
                            ->whereTaskId($this->id)
                            ->whereIn('name', $deletedTags->pluck('name'))
                            ->delete();
                    }

                    // 找出需要新增的标签（在新数据中存在，但在旧数据中不存在）
                    $addedTags = $newTags->filter(function ($newTag) use ($oldTags) {
                        return !$oldTags->contains('name', $newTag['name']);
                    });
                    if ($addedTags->isNotEmpty()) {
                        $this->addLog("新增{任务}标签", [
                            'tags' => $addedTags->values()->all()
                        ]);
                        $addedTags->each(function ($tag) {
                            ProjectTaskTag::createInstance([
                                'project_id' => $this->project_id,
                                'task_id' => $this->id,
                                'name' => $tag['name'],
                                'color' => $tag['color'],
                            ])->save();
                        });
                    }

                    // 找出需要更新的标签（标签名相同，但其他属性可能变化）
                    $updatedTags = $newTags->filter(function ($newTag) use ($oldTags) {
                        $oldTag = $oldTags->firstWhere('name', $newTag['name']);
                        return $oldTag && ($oldTag['color'] !== $newTag['color']);
                    });
                    if ($updatedTags->isNotEmpty()) {
                        $this->addLog("更新{任务}标签", [
                            'tags' => $updatedTags->values()->all()
                        ]);
                        $updatedTags->each(function ($tag) {
                            ProjectTaskTag::whereProjectId($this->project_id)
                                ->whereTaskId($this->id)
                                ->whereName($tag['name'])
                                ->update(['color' => $tag['color']]);
                        });
                    }
                }
                // 优先级
                $p = false;
                $oldPName = $this->p_name;
                if (Arr::exists($data, 'p_level') && $this->p_level != $data['p_level']) {
                    $this->p_level = intval($data['p_level']);
                    $p = true;
                }
                if (Arr::exists($data, 'p_name') && $this->p_name != $data['p_name']) {
                    $this->p_name = trim($data['p_name']);
                    $p = true;
                }
                if (Arr::exists($data, 'p_color') && $this->p_color != $data['p_color']) {
                    $this->p_color = trim($data['p_color']);
                    $p = true;
                }
                if ($p) {
                    $this->addLog("修改{任务}优先级", [
                        'change' => [$oldPName, $this->p_name]
                    ]);
                }
            }
            $this->save();
        }, '', $projectLockIds);
        return true;
    }

    /**
     * 刷新重复周期时间
     * @param bool $save 是否执行保存
     * @return bool
     */
    public function refreshLoop($save = false)
    {
        $success = true;
        if ($this->start_at) {
            $base = Carbon::parse($this->start_at);
            if ($base->lt(Carbon::today())) {
                // 如果任务开始时间小于今天则基数时间为今天
                $base = Carbon::parse(date("Y-m-d {$base->toTimeString()}"));
            }
        } else {
            // 未设置任务时间时基数时间为今天
            $base = Carbon::today();
        }
        switch ($this->loop) {
            case "day":
                $this->loop_at = $base->addDay();
                break;
            case "weekdays":
                $this->loop_at = $base->addWeekday();
                break;
            case "week":
                $this->loop_at = $base->addWeek();
                break;
            case "twoweeks":
                $this->loop_at = $base->addWeeks(2);
                break;
            case "month":
                $this->loop_at = $base->addMonth();
                break;
            case "year":
                $this->loop_at = $base->addYear();
                break;
            case "never":
                $this->loop_at = null;
                break;
            default:
                // 自定义天数须为正整数：Carbon 3 的 addDays 不接受字符串，0 天会让新任务立刻再次命中周期
                $days = Base::isNumber($this->loop) ? (int)$this->loop : 0;
                if ($days > 0) {
                    $this->loop_at = $base->addDays($days);
                } else {
                    $this->loop_at = null;
                    $success = false;
                }
                break;
        }
        if ($success && $save) {
            $this->save();
        }
        return $success;
    }

    /**
     * 获取周期描述
     * @return string
     */
    public function loopDesc() {
        $loopDesc = "从不";
        switch ($this->loop) {
            case "day":
                $loopDesc = "每天";
                break;
            case "weekdays":
                $loopDesc = "每个工作日";
                break;
            case "week":
                $loopDesc = "每周";
                break;
            case "twoweeks":
                $loopDesc = "每两周";
                break;
            case "month":
                $loopDesc = "每月";
                break;
            case "year":
                $loopDesc = "每年";
                break;
            default:
                if (Base::isNumber($this->loop)) {
                    $loopDesc = "每{$this->loop}天";
                }
                break;
        }
        return $loopDesc;
    }

    /**
     * 复制任务
     * @param int|null $projectId 目标项目，默认沿用源任务
     * @param int|null $columnId 目标列表，默认沿用源任务
     * @param int|null $parentId 目标父任务，默认沿用源任务
     * @param bool $checkUserLimit 是否检查复制成员的个人任务上限
     * @param bool $checkPlacementLimit 是否检查目标项目、列表和子任务容量
     * @return self
     */
    public function copyTask(?int $projectId = null, ?int $columnId = null, ?int $parentId = null, bool $checkUserLimit = true, bool $checkPlacementLimit = true)
    {
        $source = $this->fresh(['content', 'taskFile', 'taskUser', 'taskTag']);
        if (!$source) {
            throw new ApiException('任务不存在');
        }

        return AbstractModel::transaction(function () use ($source, $projectId, $columnId, $parentId, $checkUserLimit, $checkPlacementLimit) {
            // 复制任务（使用最新数据，避免复制临时字段）
            $task = $source->replicate();
            $task->project_id = $projectId ?? $source->project_id;
            $task->column_id = $columnId ?? $source->column_id;
            $task->parent_id = $parentId ?? $source->parent_id;
            // Capacity checks serialize on the project row. Lock it before a
            // parent task so copies use the same project -> task order as adds
            // and moves, avoiding a parent -> project deadlock cycle.
            Project::query()->whereKey($task->project_id)->lockForUpdate()->firstOrFail();
            if ($task->parent_id > 0) {
                $parentTask = self::whereKey($task->parent_id)->lockForUpdate()->first();
                if (!$parentTask || (int)$parentTask->project_id !== (int)$task->project_id) {
                    throw new ApiException('子任务格式错误');
                }
            }
            if ($task->project_id != $source->project_id && $source->milestone_id) {
                $milestoneName = ProjectMilestone::whereProjectId($source->project_id)
                    ->whereKey($source->milestone_id)
                    ->value('name');
                if ($milestoneName) {
                    $targetMilestones = ProjectMilestone::whereProjectId($task->project_id)
                        ->where('name', $milestoneName)
                        ->orderBy('id')
                        ->limit(2)
                        ->pluck('id');
                    if ($targetMilestones->count() > 1) {
                        throw new ApiException('目标项目存在重名里程碑，无法复制任务关联');
                    }
                    $task->milestone_id = $targetMilestones->first();
                } else {
                    $task->milestone_id = null;
                }
            }
            $task->dialog_id = 0;
            $task->archived_at = null;
            $task->archived_userid = 0;
            $task->archived_follow = 0;
            $task->complete_at = null;
            $task->created_at = Carbon::now();
            if ($checkPlacementLimit) {
                self::assertTaskPlacementLimit($task->project_id, $task->column_id);
                if ($task->parent_id > 0 && ProjectTask::whereParentId($task->parent_id)
                        ->whereNull('project_tasks.complete_at')
                        ->whereNull('project_tasks.archived_at')
                        ->count() >= 50) {
                    throw new ApiException('每个任务的子任务最多不能超过50个');
                }
            }
            if ($checkUserLimit) {
                self::assertUserTaskLimit($source->taskUser->pluck('userid')->toArray());
            }
            $task->save();
            // 复制任务内容
            if ($source->content) {
                $tmp = $source->content->replicate();
                $tmp->project_id = $task->project_id;
                $tmp->task_id = $task->id;
                $tmp->created_at = Carbon::now();
                $tmp->save();
            }
            // 复制任务附件
            foreach ($source->taskFile as $taskFile) {
                $tmp = $taskFile->replicate();
                $tmp->project_id = $task->project_id;
                $tmp->task_id = $task->id;
                $tmp->created_at = Carbon::now();
                $tmp->save();
            }
            // 复制任务标签快照
            foreach ($source->taskTag as $taskTag) {
                $tmp = $taskTag->replicate();
                $tmp->project_id = $task->project_id;
                $tmp->task_id = $task->id;
                $tmp->created_at = Carbon::now();
                $tmp->save();
            }
            // 复制任务成员
            foreach ($source->taskUser as $taskUser) {
                $tmp = $taskUser->replicate();
                $tmp->project_id = $task->project_id;
                $tmp->task_id = $task->id;
                $tmp->task_pid = $task->parent_id ?: $task->id;
                $tmp->created_at = Carbon::now();
                $tmp->save();
            }
            //
            ProjectTaskHandoffRecord::created($task, 'copy');
            return $task;
        });
    }

    /**
     * 获取项目的工作流状态项（start 和 end）
     * @param int $projectId 项目ID
     * @return array ['start' => ProjectFlowItem|null, 'end' => ProjectFlowItem|null]
     */
    public static function getProjectFlowItems(int $projectId): array
    {
        $startFlowItem = null;
        $endFlowItem = null;
        $projectFlow = ProjectFlow::whereProjectId($projectId)->orderByDesc('id')->first();
        if ($projectFlow) {
            $flowItems = ProjectFlowItem::whereFlowId($projectFlow->id)->orderBy('sort')->get();
            foreach ($flowItems as $item) {
                if ($item->status == 'start' && !$startFlowItem) {
                    $startFlowItem = $item;
                }
                if ($item->status == 'end' && !$endFlowItem) {
                    $endFlowItem = $item;
                }
            }
        }
        return ['start' => $startFlowItem, 'end' => $endFlowItem];
    }

    /**
     * 生成工作流状态名称
     * @param ProjectFlowItem|null $flowItem
     * @return string
     */
    public static function formatFlowItemName(?ProjectFlowItem $flowItem): string
    {
        return $flowItem ? ($flowItem->status . '|' . $flowItem->name . '|' . $flowItem->color) : '';
    }

    /**
     * 复制子任务到新的父任务
     * @param ProjectTask $newParentTask 新的父任务
     * @param array $options 选项
     *     - reset_complete: 是否重置完成状态并映射到 start 工作流（默认 true）
     *     - sync_time: 是否同步时间到父任务的时间（默认 false）
     *     - update_project: 是否更新项目相关字段（project_id、column_id）（默认 false）
     * @return array 新创建的子任务数组
     */
    public function copySubTasks(ProjectTask $newParentTask, array $options = []): array
    {
        $resetComplete = $options['reset_complete'] ?? true;
        $syncTime = $options['sync_time'] ?? false;
        $updateProject = $options['update_project'] ?? false;

        self::taskSubtreeDescendants($this->id, 50);
        $newSubTasks = [];
        $subTasks = self::where('project_id', $this->project_id)->whereParentId($this->id)->get();
        if ($subTasks->isEmpty()) {
            return $newSubTasks;
        }

        // 获取 start 工作流状态
        $flowItems = $resetComplete ? self::getProjectFlowItems($newParentTask->project_id) : ['start' => null];
        $startFlowItem = $flowItems['start'];

        foreach ($subTasks as $subTask) {
            $targetProjectId = $updateProject ? $newParentTask->project_id : $subTask->project_id;
            $targetColumnId = $updateProject ? $newParentTask->column_id : $subTask->column_id;
            $newSubTask = $subTask->copyTask($targetProjectId, $targetColumnId, $newParentTask->id);

            // 同步时间
            if ($syncTime) {
                $newSubTask->start_at = $newParentTask->start_at;
                $newSubTask->end_at = $newParentTask->end_at;
            }

            // 更新项目相关字段
            if ($updateProject) {
                $newSubTask->project_id = $newParentTask->project_id;
                $newSubTask->column_id = $newParentTask->column_id;
            }

            // 重置完成状态
            if ($resetComplete) {
                $newSubTask->complete_at = null;
                $newSubTask->flow_item_id = $startFlowItem?->id ?? 0;
                $newSubTask->flow_item_name = self::formatFlowItemName($startFlowItem);
            }

            $newSubTask->save();
            $newSubTasks[] = $newSubTask;
            $newSubTasks = array_merge($newSubTasks, $subTask->copySubTasks($newSubTask, $options));
        }

        return $newSubTasks;
    }

    /**
     * 移动子任务到新项目/列
     * @param int $projectId 目标项目ID
     * @param int $columnId 目标列ID
     */
    public function moveSubTasks(int $projectId, int $columnId, ?int $sourceProjectId = null, ?array $targetMemberIds = null, ?array $milestoneMap = null): void
    {
        $sourceProjectId ??= (int)$this->project_id;
        $subTasks = self::withTrashed()->where('project_id', $sourceProjectId)->whereParentId($this->id)->get();
        if ($subTasks->isEmpty()) {
            return;
        }

        $flowItems = self::getProjectFlowItems($projectId);
        $startFlowItem = $flowItems['start'];
        $endFlowItem = $flowItems['end'];

        foreach ($subTasks as $subTask) {
            $subTask->project_id = $projectId;
            $subTask->column_id = $columnId;
            // 根据完成状态映射工作流
            if ($subTask->complete_at) {
                $subTask->flow_item_id = $endFlowItem?->id ?? 0;
                $subTask->flow_item_name = self::formatFlowItemName($endFlowItem);
            } else {
                $subTask->flow_item_id = $startFlowItem?->id ?? 0;
                $subTask->flow_item_name = self::formatFlowItemName($startFlowItem);
            }
            if ($milestoneMap !== null) {
                $subTask->milestone_id = $milestoneMap[$subTask->id] ?? null;
            }
            self::syncMovedTaskRelations($subTask->id, $projectId, $targetMemberIds);
            $subTask->save();
            $subTask->moveSubTasks($projectId, $columnId, $sourceProjectId, $targetMemberIds, $milestoneMap);
        }
    }

    /**
     * Keep every project-scoped task relation aligned when a task changes projects.
     */
    private static function syncMovedTaskRelations(int $taskId, int $projectId, ?array $targetMemberIds = null): void
    {
        ProjectTaskContent::whereTaskId($taskId)->update(['project_id' => $projectId]);
        ProjectTaskFile::whereTaskId($taskId)->update(['project_id' => $projectId]);
        ProjectTaskTag::whereTaskId($taskId)->update(['project_id' => $projectId]);
        ProjectTaskUser::whereTaskId($taskId)->update(['project_id' => $projectId]);
        ProjectTaskVisibilityUser::whereTaskId($taskId)->update(['project_id' => $projectId]);

        if ($targetMemberIds !== null) {
            ProjectTaskUser::whereTaskId($taskId)->whereNotIn('userid', $targetMemberIds)->delete();
            ProjectTaskVisibilityUser::whereTaskId($taskId)->whereNotIn('userid', $targetMemberIds)->delete();
        }
    }

    /** @param array<int> $taskIds @return array<int, int|null> */
    private static function projectMoveMilestoneMap(array $taskIds, int $sourceProjectId, int $targetProjectId): array
    {
        $taskMilestones = collect();
        foreach (array_chunk($taskIds, 1000) as $taskIdBatch) {
            $taskMilestones = $taskMilestones->union(self::withTrashed()->whereIn('id', $taskIdBatch)
                ->whereNotNull('milestone_id')
                ->pluck('milestone_id', 'id'));
        }
        if ($taskMilestones->isEmpty()) {
            return [];
        }

        $sourceMilestoneNames = collect();
        foreach (array_chunk($taskMilestones->values()->unique()->all(), 1000) as $milestoneIdBatch) {
            $sourceMilestoneNames = $sourceMilestoneNames->union(ProjectMilestone::whereProjectId($sourceProjectId)
                ->whereIn('id', $milestoneIdBatch)
                ->pluck('name', 'id'));
        }
        $milestoneNames = $sourceMilestoneNames->values()->unique()->all();
        $targetMilestonesByName = collect();
        foreach (array_chunk($milestoneNames, 1000) as $milestoneNameBatch) {
            $targetMilestonesByName = $targetMilestonesByName->union(ProjectMilestone::whereProjectId($targetProjectId)
                ->whereIn('name', $milestoneNameBatch)
                ->get(['id', 'name'])
                ->groupBy('name')
                ->map(fn($milestones) => $milestones->count() === 1 ? (int)$milestones->first()->id : null));
        }

        $map = [];
        foreach ($taskMilestones as $taskId => $milestoneId) {
            $name = $sourceMilestoneNames->get($milestoneId);
            $map[(int)$taskId] = $name !== null ? $targetMilestonesByName->get($name) : null;
        }
        return $map;
    }

    /**
     * 同步项目成员至聊天室
     */
    public function syncDialogUser()
    {
        if ($this->parent_id > 0) {
            $task = self::where('project_id', $this->project_id)->find($this->parent_id);
            $task?->syncDialogUser();
            return;
        }
        if (empty($this->dialog_id)) {
            return;
        }
        AbstractModel::transaction(function() {
            $userids = $this->relationUserids();
            foreach ($userids as $userid) {
                WebSocketDialogUser::updateInsert([
                    'dialog_id' => $this->dialog_id,
                    'userid' => $userid,
                ], [
                    'important' => 1
                ], function () use ($userid) {
                    return [
                        'important' => 1,
                        'bot' => User::isBot($userid) ? 1 : 0,
                    ];
                });
            }
            WebSocketDialogUser::whereDialogId($this->dialog_id)->whereNotIn('userid', $userids)->whereImportant(1)->remove();
        });
    }

    /**
     * 获取任务所有人员（负责人、协助人员、子任务负责人）
     * @return array
     */
    public function relationUserids()
    {
        $userids = ProjectTaskUser::whereTaskId($this->id)->orderByDesc('owner')->orderByDesc('id')->pluck('userid')->toArray();
        $taskIds = self::taskSubtreeDescendants($this->id)
            ->whereNull('archived_at')
            ->pluck('id')->all();
        if ($taskIds) {
            $userids = array_merge($userids, ProjectTaskUser::whereIn('task_id', $taskIds)->pluck('userid')->toArray());
        }
        return array_values(array_filter(array_unique($userids)));
    }

    /**
     * 会员id是否在项目里
     * @param int $userid
     * @return int 0:不存在、1存在、2存在且是管理员
     */
    public function useridInTheProject($userid)
    {
        $user = ProjectUser::whereProjectId($this->project_id)->whereUserid(intval($userid))->first();
        if (empty($user)) {
            return 0;
        }
        return $user->owner ? 2 : 1;
    }

    /**
     * 会员id是否在任务里
     * @param int $userid
     * @return int 0:不存在、1存在、2存在且是管理员
     */
    public function useridInTheTask($userid)
    {
        $user = ProjectTaskUser::whereTaskId($this->id)->whereUserid(intval($userid))->first();
        if (empty($user)) {
            return 0;
        }
        return $user->owner ? 2 : 1;
    }

    /**
     * 权限版本
     * @param int $level
     * 1：负责人
     * 2：协助人/负责人
     * 3：创建人/协助人/负责人
     * 4：任务群聊成员/3
     * @return bool
     */
    public function permission($level = 1)
    {
        if ($level >= 4) {
            return $this->permission(3) || $this->existDialogUser();
        }
        if ($level >= 3 && $this->isCreater()) {
            return true;
        }
        if ($level >= 2 && $this->isAssister()) {
            return true;
        }
        return $this->isOwner();
    }

    /**
     * 判断是否在任务对话里
     * @return bool
     */
    public function existDialogUser()
    {
        return $this->dialog_id && WebSocketDialogUser::whereDialogId($this->dialog_id)->whereUserid(User::userid())->exists();
    }

    /**
     * 判断是否创建者
     * @return bool
     */
    public function isCreater()
    {
        return $this->userid == User::userid();
    }

    /**
     * 判断是否协助人员
     * @return bool
     */
    public function isAssister()
    {
        $projectId = (int)$this->project_id;
        $row = $this;
        while ($row) {
            if (ProjectTaskUser::whereTaskId($row->id)->whereUserid(User::userid())->whereOwner(0)->exists()) {
                return true;
            }
            if ($row->parent_id <= 0) {
                break;
            }
            $row = self::where('project_id', $projectId)->find($row->parent_id);
        }
        return false;
    }

    /**
     * 判断是否负责人（或者是主任务的负责人）
     * @return bool
     */
    public function isOwner()
    {
        if ($this->owner) {
            return true;
        }
        $projectId = (int)$this->project_id;
        $row = $this;
        while ($row) {
            if (ProjectTaskUser::whereTaskId($row->id)->whereUserid(User::userid())->whereOwner(1)->exists()) {
                return true;
            }
            if ($row->parent_id <= 0) {
                break;
            }
            $row = self::where('project_id', $projectId)->find($row->parent_id);
        }
        return false;
    }

    /**
     * 是否有负责人
     * @return bool
     */
    public function hasOwner()
    {
        if (!isset($this->appendattrs['has_owner'])) {
            $this->appendattrs['has_owner'] = ProjectTaskUser::whereTaskId($this->id)->whereOwner(1)->exists();
        }
        return $this->appendattrs['has_owner'];
    }

    /**
     * 检查并自动设置工作流状态
     * @param string $status 目标状态类型 ('start' 或 'end')
     * @param int $errorCode 多状态时的错误码 (-4005 或 -4006)
     * @return string|null 自动设置的状态名称，无状态时返回 null
     */
    private function checkAndAutoSetFlowItem(string $status, int $errorCode): ?string
    {
        $flowItems = ProjectFlowItem::whereProjectId($this->project_id)
            ->whereStatus($status)
            ->get(['id', 'name', 'status', 'color']);

        if ($flowItems->count() > 1) {
            $msg = $status === 'end' ? '存在多个结束状态，请选择要使用的状态' : '存在多个开始状态，请选择要使用的状态';
            throw new ApiException($msg, [
                'task_id' => $this->id,
                'flow_items' => $flowItems->toArray(),
            ], $errorCode);
        }

        if ($flowItems->count() == 1) {
            $autoFlowItem = $flowItems->first();
            $oldFlowItemId = $this->flow_item_id;
            $oldFlowItemName = $this->flow_item_name;
            $this->flow_item_id = $autoFlowItem->id;
            $this->flow_item_name = $autoFlowItem->status . "|" . $autoFlowItem->name . "|" . $autoFlowItem->color;

            if ($oldFlowItemId != $this->flow_item_id) {
                ProjectTaskFlowChange::createInstance([
                    'task_id' => $this->id,
                    'userid' => User::userid(),
                    'before_flow_item_id' => $oldFlowItemId,
                    'before_flow_item_name' => $oldFlowItemName,
                    'after_flow_item_id' => $this->flow_item_id,
                    'after_flow_item_name' => $this->flow_item_name,
                ])->save();
            }
            return $autoFlowItem->name;
        }

        return null;
    }

    /**
     * 标记已完成、未完成
     * @param Carbon|null $complete_at 完成时间
     * @param String $complete_name 已完成名称（留空为：已完成）
     * @return bool
     */
    public function completeTask($complete_at, $complete_name = null)
    {
        ProjectTaskHandoffRecord::track($this, 'complete', function () use ($complete_at, $complete_name) {
            $addMsg = $this->parent_id == 0 && $this->dialog_id > 0;
            if ($complete_at === null) {
                // 标记未完成
                if (!$this->complete_at) {
                    return; // 本来就未完成
                }
                $this->complete_at = null;
                $this->addLog("标记{任务}未完成");
                if ($addMsg) {
                    WebSocketDialogMsg::sendMsg(null, $this->dialog_id, 'notice', [
                        'notice' => "标记任务未完成"
                    ], 0, true, true);
                }
            } else {
                // 标记已完成
                if ($this->complete_at) {
                    return; // 本来就已完成
                }
                if (self::taskSubtreeDescendants($this->id)->contains(fn($task) => !$task->complete_at)) {
                    throw new ApiException('子任务未完成', [
                        'task_id' => $this->id
                    ], -4004);
                }
                if (!$this->hasOwner()) {
                    throw new ApiException('请先领取任务');
                }
                if ($this->overdue) {
                    $this->addLog("{任务}超期未完成", [
                        'cache' => [
                            'task_at' => $this->start_at . '~' . $this->end_at,
                            'over_sec' => (int)Carbon::now()->diffInSeconds($this->end_at, true),
                            'owners' => $this->taskUser->where('owner', 1)->pluck('userid')->toArray(),
                            'assists' => $this->taskUser->where('owner', 0)->pluck('userid')->toArray(),
                        ]
                    ]);
                }
                if (empty($complete_name)) {
                    $complete_name = '已完成';
                }
                $this->complete_at = $complete_at;
                $this->addLog("标记{任务}{$complete_name}");
                if ($addMsg) {
                    WebSocketDialogMsg::sendMsg(null, $this->dialog_id, 'notice', [
                        'notice' => "标记任务{$complete_name}"
                    ], 0, true, true);
                }
            }
            $this->save();
        });
        return true;
    }

    /**
     * 归档任务、取消归档
     * @param Carbon|null $archived_at 归档时间
     * @return bool
     */
    public function archivedTask($archived_at, $isAuto = false)
    {
        if (!$this->complete_at) {
            $flowItems = ProjectFlowItem::whereProjectId($this->project_id)->whereStatus('end')->pluck('name');
            if ($flowItems) {
                $flowItems = implode(",", array_values(array_unique($flowItems->toArray())));
            }
            if (empty($flowItems)) {
                $flowItems = "已完成";
            }
            throw new ApiException('仅限【' . $flowItems . '】状态的任务归档');
        }
        ProjectTaskHandoffRecord::track($this, $isAuto ? 'auto_archive' : 'archive', function () use ($isAuto, $archived_at) {
            if ($archived_at === null) {
                // 还原任务栏
                if (!$this->projectColumn) {
                    $this->projectColumn()->restore();
                    if($projectColumn = $this->projectColumn()->first()){
                        $projectColumn->pushMsg('recovery', $projectColumn);
                    }
                }
                // 取消归档
                $this->archived_at = null;
                $this->archived_userid = User::userid();
                $this->archived_follow = 0;
                $logText = "任务取消归档";
                $userid = 0;
            } else {
                // 归档任务
                if ($isAuto === true) {
                    $logText = "任务自动归档";
                    $userid = 0;
                } else {
                    $logText = "任务归档";
                    $userid = User::userid();
                }
                $this->archived_at = $archived_at;
                $this->archived_userid = $userid;
                $this->archived_follow = 0;
            }
            // 添加日志
            $this->addLog($logText, [], $userid);
            // 推送状态
            $this->pushMsg($archived_at === null ? 'recovery' : 'archived', [
                'id' => $this->id,
                'archived_at' => $this->archived_at,
                'archived_userid' => $this->archived_userid,
            ]);
            // 更新对话时间
            if ($this->dialog_id > 0) {
                WebSocketDialogUser::whereDialogId($this->dialog_id)->update(['updated_at' => Carbon::now()]);  // 因为是若提醒，可以直接使用 update 更新
            }
            // 更新保存
            $descendantIds = self::taskSubtreeDescendants($this->id)->pluck('id')->all();
            if ($descendantIds) {
                self::whereIn('id', $descendantIds)->change([
                    'archived_at' => $this->archived_at,
                    'archived_userid' => $this->archived_userid,
                    'archived_follow' => $this->archived_follow,
                ]);
            }
            $this->save();
        });
        return true;
    }

    /**
     * 删除任务
     * @param bool $pushMsg 是否推送
     * @return bool
     */
    public function deleteTask($pushMsg = true)
    {
        AbstractModel::transaction(function () {
            if ($this->dialog_id) {
                $dialog = WebSocketDialog::find($this->dialog_id);
                $dialog?->deleteDialog();
            }
            $descendantIds = self::taskSubtreeDescendants($this->id)->pluck('id')->all();
            if ($descendantIds) {
                self::whereIn('id', $descendantIds)->remove();
            }
            $this->deleted_userid = User::userid();
            $this->save();
            $this->addLog("删除{任务}");
            $this->delete();
        });
        if ($pushMsg) {
            $this->pushMsg('delete');
        }
        return true;
    }

    /**
     * 还原任务
     * @param bool $pushMsg 是否推送
     * @return bool
     */
    public function restoreTask($pushMsg = true)
    {
        $projectId = (int)$this->project_id;
        AbstractModel::transaction(function () use ($projectId) {
            // Serialize restores with task creation, copying and moves, and
            // always lock the project before the task tree.
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $lockedTask = self::withTrashed()->whereKey($this->id)->lockForUpdate()->firstOrFail();
            if ((int)$lockedTask->project_id !== $projectId) {
                throw new ApiException('任务所属项目已变化，请刷新后重试');
            }
            $this->setRawAttributes($lockedTask->getAttributes(), true);
            $this->unsetRelations();

            $descendants = self::taskSubtreeDescendants($this->id, null, true, true);
            $restoringOpenCountsByColumn = [];
            $restoringOpenTaskIds = [];
            $restoringTasks = collect([$this])->concat($descendants);
            foreach ($restoringTasks as $task) {
                if ($task->trashed() && !$task->complete_at && !$task->archived_at) {
                    $restoringOpenTaskIds[] = (int)$task->id;
                    $columnId = (int)$task->column_id;
                    $restoringOpenCountsByColumn[$columnId] = ($restoringOpenCountsByColumn[$columnId] ?? 0) + 1;
                }
            }
            $totalRestoringOpenTasks = array_sum($restoringOpenCountsByColumn);
            $rootColumnId = (int)$this->column_id;
            self::assertTaskBatchPlacementLimit(
                $projectId,
                $rootColumnId,
                [],
                $totalRestoringOpenTasks,
                [],
                $restoringOpenCountsByColumn[$rootColumnId] ?? 0
            );
            foreach ($restoringOpenCountsByColumn as $columnId => $count) {
                if ((int)$columnId === $rootColumnId) {
                    continue;
                }
                self::assertTaskBatchPlacementLimit($projectId, (int)$columnId, [], 0, [], $count);
            }
            if ($restoringOpenTaskIds) {
                $restoringUserCounts = ProjectTaskUser::whereIn('task_id', $restoringOpenTaskIds)
                    ->select('userid')
                    ->selectRaw('COUNT(DISTINCT task_id) AS task_count')
                    ->groupBy('userid')
                    ->get()
                    ->mapWithKeys(fn($row) => [(int)$row->userid => (int)$row->task_count])
                    ->all();
                self::assertUserTaskBatchLimit($restoringUserCounts);
            }

            if ($this->dialog_id) {
                $dialog = WebSocketDialog::withTrashed()->find($this->dialog_id);
                $dialog?->restoreDialog();
            }
            $descendantIds = $descendants->pluck('id')->all();
            if ($descendantIds) {
                self::whereIn('id', $descendantIds)->withTrashed()->restore();
            }
            $this->addLog("还原{任务}");
            $this->restore();
        });
        if ($pushMsg) {
            $this->pushMsg('restore');
        }
        return true;
    }

    /**
     * 添加任务日志
     * @param string $detail
     * @param array $record
     * @param int $userid
     * @return ProjectLog
     */
    public function addLog($detail, $record = [], $userid = 0, $taskOnly = 0)
    {
        $detail = str_replace("{任务}", $this->parent_id ? "子任务" : "任务", $detail);
        $array = [
            'project_id' => $this->project_id,
            'column_id' => $this->column_id,
            'task_id' => $this->parent_id ?: $this->id,
            'userid' => $userid ?: User::userid(),
            'detail' => $detail,
        ];
        if ($this->parent_id) {
            $record['subtask'] = [
                'id' => $this->id,
                'parent_id' => $this->parent_id,
                'name' => $this->name,
            ];
        }
        if ($record) {
            $array['record'] = $record;
        }
        if ($taskOnly) {
            $array['task_only'] = $taskOnly;
        }
        $log = ProjectLog::createInstance($array);
        $log->save();
        return $log;
    }

    /**
     * 推送消息
     * @param string $action
     * @param array|self $data      发送内容，默认为[id, parent_id, project_id, column_id, dialog_id]
     * @param array $userid         指定会员，默认为项目所有成员
     * @param bool $ignoreSelf      是否忽略当前连接
     */
    public function pushMsg($action, $data = null, $userid = null, $ignoreSelf = true)
    {
        if (!$this->project) {
            return;
        }
        if ($data === null) {
            $data = [
                'id' => $this->id,
                'parent_id' => $this->parent_id,
                'project_id' => $this->project_id,
                'column_id' => $this->column_id,
                'dialog_id' => $this->dialog_id,
                'visibility' => $this->visibility,
            ];
        } elseif ($data instanceof self) {
            $data = $data->toArray();
        }

        // 获取接收会员
        if ($userid === null) {
            $userids = $this->project->relationUserids();
        } else {
            $userids = is_array($userid) ? $userid : [$userid];
        }
        $userids = array_values(array_unique(array_map('intval', $userids)));
        if (empty($userids)) {
            return;
        }

        // 按可见性分组推送
        $taskUser = ProjectTaskUser::select(['userid', 'owner'])->whereTaskId($data['id'])->get();
        $ownerList = $taskUser->where('owner', 1)->pluck('userid')->toArray();
        $assistList = $taskUser->where('owner', 0)->pluck('userid')->toArray();

        $ownerUsers = array_values(array_intersect($userids, $ownerList));
        $assistUsers = array_values(array_diff(array_intersect($userids, $assistList), $ownerUsers));

        $array = [];

        // 负责人
        if ($ownerUsers) {
            $array[] = [
                'userid' => $ownerUsers,
                'data' => array_merge($data, [
                    'owner' => 1,
                    'assist' => 0,
                ])
            ];
        }

        // 协助人
        if ($assistUsers) {
            $array[] = [
                'userid' => $assistUsers,
                'data' => array_merge($data, [
                    'owner' => 0,
                    'assist' => 1,
                ])
            ];
        }

        // 其他人
        $otherUsers = [];
        switch (intval($data['visibility'])) {
            case 1:
                // 项目人员：除了负责人、协助人项目其他人
                $otherUsers = array_diff($userids, $ownerUsers, $assistUsers);
                break;
            case 2:
                // 任务人员：除了负责人、协助人
                // $otherUsers = [];
                break;
            case 3:
                // 指定成员
                $specifys = ProjectTaskVisibilityUser::select(['userid'])->whereTaskId($data['id'])->pluck('userid')->toArray();
                $otherUsers = array_diff(array_intersect($userids, $specifys), $ownerUsers, $assistUsers);
                break;
        }

        if ($otherUsers) {
            $array[] = [
                'userid' => array_values($otherUsers),
                'data' => array_merge($data, [
                    'owner' => 0,
                    'assist' => 0,
                ])
            ];
        }

        if (empty($array)) {
            return;
        }

        // 推送
        foreach ($array as $item) {
            $params = [
                'ignoreFd' => $ignoreSelf ? Request::header('fd') : null,
                'userid' => $item['userid'],
                'msg' => [
                    'type' => 'projectTask',
                    'action' => $action,
                    'data' => $item['data'],
                ]
            ];
            $task = new PushTask($params, false);
            Task::deliver($task);
        }
    }

    /**
     * 添加可见性任务 推送
     * @param array|self $data 发送内容，默认为[id, parent_id, project_id, column_id, dialog_id]
     */
    public function pushMsgVisibleAdd($data = null, array $pushUserIds = [])
    {
        if (!$this->project) {
            return;
        }
        if ($data === null) {
            $data = [
                'id' => $this->id,
                'parent_id' => $this->parent_id,
                'project_id' => $this->project_id,
                'column_id' => $this->column_id,
                'dialog_id' => $this->dialog_id,
            ];
        } elseif ($data instanceof self) {
            $data = $data->toArray();
        }
        //
        $array = [];
        if ($pushUserIds) {
            $userids = $pushUserIds;
        } elseif ($this->visibility != 1) {
            $userids = ProjectTaskUser::whereTaskId($this->id)->orWhere('task_pid', '=', $this->id)->pluck('userid')->toArray();
            if ($this->visibility == 3) {
                $userids = array_merge($userids, ProjectTaskVisibilityUser::whereTaskId($this->id)->pluck('userid')->toArray());
            }
        } else {
            $userids = ProjectUser::whereProjectId($this->project_id)->pluck('userid')->toArray();  // 项目成员
        }
        //
        $array[] = [
            'userid' => array_values($userids),
            'data' => $data
        ];
        //
        foreach ($array as $item) {
            $params = [
                'ignoreFd' => '0',
                'userid' => $item['userid'],
                'msg' => [
                    'type' => 'projectTask',
                    'action' => 'add',
                    'data' => $item['data'],
                ]
            ];
            $task = new PushTask($params, false);
            Task::deliver($task);
        }
    }

    /**
     * 删除可见性任务 推送
     * @param array $userids
     * @return void
     */
    public function pushMsgVisibleRemove(array $userids = [])
    {
        if (!$this->project) {
            return;
        }
        $data = [
            'id' => $this->id,
            'parent_id' => $this->parent_id,
            'project_id' => $this->project_id,
            'column_id' => $this->column_id,
            'dialog_id' => $this->dialog_id,
        ];
        //
        $projectOwnerids = ProjectUser::whereProjectId($this->project_id)
            ->whereIn('owner', [ProjectUser::OWNER_PRIMARY, ProjectUser::OWNER_DEPUTY])
            ->pluck('userid')->toArray();  // 项目负责人（含项目管理员）
        //
        $array = [];
        if (empty($userids)) {
            // 默认 项目成员 与 项目负责人，任务负责人、协助人的差集
            $projectUserids = ProjectUser::whereProjectId($this->project_id)->pluck('userid')->toArray();  // 项目成员
            $taskOwnerAndAssists = ProjectTaskUser::select(['userid', 'owner'])->whereIn('owner', [0, 1])->whereTaskId($this->id)->pluck('userid')->toArray();
            $subUserids = ProjectTaskUser::whereTaskPid($this->id)->pluck('userid')->toArray();
            $userids = array_diff($projectUserids, $projectOwnerids, $taskOwnerAndAssists, $subUserids);
        } else {
            // 保证项目负责人都能看到
            $userids = array_diff($userids, $projectOwnerids);
        }
        //
        $array[] = [
            'userid' => array_values($userids),
            'data' => $data
        ];
        //
        foreach ($array as $item) {
            $params = [
                'ignoreFd' => '0',
                'userid' => $item['userid'],
                'msg' => [
                    'type' => 'projectTask',
                    'action' => 'delete',
                    'data' => $item['data'],
                ]
            ];
            $task = new PushTask($params, false);
            Task::deliver($task);
        }
    }

    /**
     * 更新可见性任务 推送
     * @param array|self $data 发送内容，默认为[id, parent_id, project_id, column_id, dialog_id]
     */
    public function pushMsgVisibleUpdate($data, array $deleteUserIds = [], array $addUserIds = [])
    {
        if ($deleteUserIds) {
            $this->pushMsgVisibleRemove($deleteUserIds);
        }
        if ($addUserIds) {
            $this->pushMsgVisibleAdd($data, $addUserIds);
        }
    }

    /**
     * 任务提醒
     * @param $userids
     * @param int $type 0-新任务、1-即将超时、2-已超时、3-修改时间
     * @param string $suffix 描述后缀
     * @return void
     */
    public function taskPush($userids, int $type, string $suffix = "")
    {
        if ($userids === null) {
            $userids = $this->taskUser->pluck('userid')->toArray();
        }
        if (empty($userids)) {
            return;
        }
        $owners = $this->taskUser->pluck('owner', 'userid')->toArray();
        $receivers = User::whereIn('userid', $userids)->whereNull('disable_at')->get();
        if (empty($receivers)) {
            return;
        }
        //
        $userid = User::userid();
        //
        $botUser = User::botGetOrCreate('task-alert');
        if (empty($botUser)) {
            return;
        }
        /** @var User $user */
        foreach ($receivers as $receiver) {
            $data = [
                'type' => $type,
                'userid' => $receiver->userid,
                'task_id' => $this->id,
            ];
            if (in_array($type, [1, 2]) && ProjectTaskPushLog::where($data)->exists()) {
                continue;
            }
            if ($owners[$receiver->userid]) {
                $title = match ($type) {
                    1 => "您负责的任务即将超时",
                    2 => "您负责的任务已经超时",
                    3 => "您负责的任务时间已修改",
                    default => "您有一个新任务",
                };
            } else {
                $title = match ($type) {
                    1 => "您协助的任务即将超时",
                    2 => "您协助的任务已经超时",
                    3 => "您协助的任务时间已修改",
                    default => "您有一个新协助任务",
                };
            }
            //
            $dialog = WebSocketDialog::checkUserDialog($botUser, $receiver->userid);
            if ($dialog) {
                ProjectTaskPushLog::createInstance($data)->save();
                WebSocketDialogMsg::sendMsg(null, $dialog->id, 'template', [
                    'type' => 'task_list',
                    'title' => $title . $suffix,
                    'list' => [
                        [
                            'id' => $this->parent_id ?: $this->id,
                            'name' => $this->name,
                        ]
                    ],
                ], in_array($type, [0, 3]) ? $userid : $botUser->userid);
            }
        }
    }

    /**
     * 移动任务
     * @param int $projectId
     * @param int $columnId
     * @param int $flowItemId
     * @param array $owner
     * @param array $assist
     * @param string|null $completed
     * @return bool
     */
    public function moveTask(int $projectId, int $columnId, int $flowItemId = 0, array $owner = [], array $assist = [], ?string $completed = null)
    {
        $sourceProjectId = (int)$this->project_id;
        // Capacity checks run for same-project status/list changes too, so
        // serialize every move on its project row before locking the task.
        $projectLockIds = [$sourceProjectId, $projectId];
        ProjectTaskHandoffRecord::track($this, 'move', function () use ($sourceProjectId, $projectId, $columnId, $flowItemId, $owner, $assist, $completed) {
            //
            if ((int)$this->project_id !== $sourceProjectId) {
                throw new ApiException('任务所属项目已变化，请刷新后重试');
            }
            if ($this->parent_id > 0) {
                $parentTask = self::where('project_id', $this->project_id)->find($this->parent_id);
                if (!$parentTask) {
                    throw new ApiException('子任务格式错误');
                }
                if ((int)$this->project_id !== $projectId) {
                    throw new ApiException('子任务格式错误');
                }
            }
            $oldProject = Project::userProject($this->project_id);
            ProjectPermission::userTaskPermission($oldProject, ProjectPermission::TASK_MOVE, $this);
            $newProject = Project::userProject($projectId);
            if ((int)$oldProject->id !== (int)$newProject->id) {
                ProjectPermission::userTaskPermission($newProject, ProjectPermission::TASK_ADD);
            }
            if (!$oldProject || !$newProject) {
                throw new ApiException('项目不存在');
            }
            $targetMemberIds = $oldProject->id !== $newProject->id
                ? ProjectUser::whereProjectId($projectId)->pluck('userid')->map(fn($userid) => (int)$userid)->all()
                : null;
            $targetFlowItem = null;
            if ($flowItemId) {
                $targetFlowItem = ProjectFlowItem::whereProjectId($projectId)->whereId($flowItemId)->first();
                if (!$targetFlowItem) {
                    throw new ApiException('任务状态不存在');
                }
            }
            $placementChanged = (int)$oldProject->id !== (int)$newProject->id || (int)$this->column_id !== $columnId;
            $rootWillBeOpen = $targetFlowItem
                ? $targetFlowItem->status !== 'end'
                : ($completed !== null ? !$completed : !$this->complete_at);
            // Soft-deleted descendants must move with their live parent. Restoring
            // them later should not recreate a cross-project parent relationship.
            $movingSubtasks = self::taskSubtreeDescendants($this->id, null, true);
            $movingTaskIds = [$this->id];
            $capacityExcludedProjectIds = [];
            $incomingOpenTaskCount = $rootWillBeOpen && !$this->archived_at ? 1 : 0;
            if ((int)$this->project_id === $projectId && !$this->complete_at && !$this->archived_at) {
                $capacityExcludedProjectIds[] = (int)$this->id;
            }
            foreach ($movingSubtasks as $movingSubtask) {
                $movingTaskIds[] = $movingSubtask->id;
                if (!$movingSubtask->trashed() && !$movingSubtask->complete_at && !$movingSubtask->archived_at) {
                    $incomingOpenTaskCount++;
                    if ((int)$this->project_id === $projectId) {
                        $capacityExcludedProjectIds[] = (int)$movingSubtask->id;
                    }
                }
            }
            $capacityExcludedColumnIds = $placementChanged
                ? $capacityExcludedProjectIds
                : ((!$this->complete_at && !$this->archived_at) ? [(int)$this->id] : []);
            $incomingOpenColumnTaskCount = $placementChanged
                ? $incomingOpenTaskCount
                : ($rootWillBeOpen && !$this->archived_at ? 1 : 0);
            self::assertTaskBatchPlacementLimit(
                $projectId,
                $columnId,
                $capacityExcludedProjectIds,
                $incomingOpenTaskCount,
                $capacityExcludedColumnIds,
                $incomingOpenColumnTaskCount
            );
            $milestoneMap = $targetMemberIds !== null
                ? self::projectMoveMilestoneMap($movingTaskIds, (int)$oldProject->id, $projectId)
                : null;
            if ($targetMemberIds !== null) {
                ProjectTaskDependencyService::handleProjectMove((int)$oldProject->id, $projectId, $movingTaskIds);
                ProjectTaskWorkService::handleProjectMove((int)$oldProject->id, $projectId, $movingTaskIds);
            }
            //
            $this->project_id = $projectId;
            $this->column_id = $columnId;
            if ($milestoneMap !== null) {
                $this->milestone_id = $milestoneMap[$this->id] ?? null;
            }
            // Only record a placement move when the project or column actually changed.
            // Same-column state/assignee edits have their own activity records below.
            $log = $placementChanged ? $this->addLog("移动{任务}", [
                'change' => [$oldProject->name, $newProject->name]
            ]) : null;
            if ($this->dialog_id && $placementChanged) {
                $notice = $oldProject->id != $newProject->id ? "「{$oldProject->name}」移动至「{$newProject->name}」" : $log->detail;
                WebSocketDialogMsg::sendMsg(null, $this->dialog_id, 'notice', ['notice' => $notice], User::userid(), true, true);
            }
            // 移动全部历史内容、附件、标签和权限关联，避免关系仍归属旧项目。
            self::syncMovedTaskRelations($this->id, $projectId, $targetMemberIds);
            // 任务用户
            $this->updateTask([
                'owner' => $owner,
                'assist' => $assist
            ]);
            // 子任务只在项目/列表位置真的变化时跟随移动；同位置更新状态或成员
            // 不应重置子任务各自的工作流状态。
            if ($placementChanged) {
                $this->moveSubTasks($projectId, $columnId, (int)$oldProject->id, $targetMemberIds, $milestoneMap);
            }
            // 子任务成员会随项目成员范围调整，重新同步任务群聊成员。
            $this->syncDialogUser();
            //
            if ($flowItemId) {
                // 更新任务流程
                $flowItem = $targetFlowItem;
                $this->flow_item_id = $flowItemId;
                $this->flow_item_name = $flowItem->status . "|" . $flowItem->name . "|" . $flowItem->color;
                if ($flowItem->status == 'end') {
                    $this->completeTask(Carbon::now(), $flowItem->name);
                } else {
                    if ($this->complete_at) {
                        self::assertTaskPlacementLimit($projectId, $columnId, $this->id);
                        self::assertUserTaskLimit(ProjectTaskUser::whereTaskId($this->id)->pluck('userid')->toArray(), $this->id);
                    }
                    $this->completeTask(null);
                }
            } else {
                // 没有流程只更新状态
                $this->flow_item_id = 0;
                $this->flow_item_name = '';
                if ($completed !== null) {
                    if (!$completed && $this->complete_at) {
                        self::assertTaskPlacementLimit($projectId, $columnId, $this->id);
                        self::assertUserTaskLimit(ProjectTaskUser::whereTaskId($this->id)->pluck('userid')->toArray(), $this->id);
                    }
                    $this->completeTask($completed ? Carbon::now(): null);
                }
            }
            //
            $this->save();
        }, '', $projectLockIds);
        $this->pushMsg('update');
        return true;
    }

    /**
     * 获取任务
     * @param $task_id
     * @return ProjectTask|\Illuminate\Database\Eloquent\Builder|\Illuminate\Database\Eloquent\Model|object|null
     */
    public static function oneTask($task_id)
    {
        $data = self::with(['taskUser', 'taskTag'])->allData()->where("project_tasks.id", intval($task_id))->first();
        if ($data && $data->parent_id === 0) {
            if ($data->owner || ProjectTaskUser::select(['owner'])->whereTaskId($data->id)->whereUserid(User::userid())->exists()) {
                $data->assist = 1;
            } else {
                $data->assist = 0;
            }
        }
        return $data;
    }

    /**
     * 获取任务（会员有任务权限 或 会员存在项目内）
     * @param int $task_id
     * @param bool $archived true:仅限未归档, false:仅限已归档, null:不限制
     * @param bool $trashed true:仅限未删除, false:仅限已删除, null:不限制
     * @param array $with
     * @return self
     */
    public static function userTask($task_id, $archived = true, $trashed = true, $with = [])
    {
        $builder = self::with($with)->allData()->where("project_tasks.id", intval($task_id));
        if ($trashed === false) {
            $builder->onlyTrashed();
        } elseif ($trashed === null) {
            $builder->withTrashed();
        }
        $task = $builder->first();
        //
        if (empty($task)) {
            throw new ApiException('任务不存在', ['task_id' => $task_id], -4002);
        }
        if ($archived === true && $task->archived_at != null) {
            throw new ApiException('任务已归档', ['task_id' => $task_id]);
        }
        if ($archived === false && $task->archived_at == null) {
            throw new ApiException('任务未归档', ['task_id' => $task_id]);
        }
        //
        try {
            $project = Project::userProject($task->project_id);
        } catch (\Throwable $e) {
            if ($task->owner !== null || $task->permission(4)) {
                $project = Project::withTrashed()->find($task->project_id);
                if (empty($project)) {
                    throw new ApiException('项目不存在或已被删除', [ 'task_id' => $task_id ], -4002);
                }
            } else {
                throw new ApiException($e->getMessage(), [ 'task_id' => $task_id ], -4002);
            }
        }
        //
        return $task;
    }

    /**
     * 获取任务（含部门负责人只读视角兜底）
     * @param int $task_id
     * @param null|bool $archived true:仅限未归档, false:仅限已归档, null:不限制
     * @param null|bool $trashed true:仅限未删除, false:仅限已删除, null:不限制
     * @param array $with
     * @return self
     */
    public static function findForDepartmentView($task_id, $archived = true, $trashed = true, $with = [])
    {
        $user = User::auth();
        $departmentView = UserDepartment::ownerViewContext($user, true);
        if ($departmentView['enabled']) {
            $builder = self::with($with)->allData()->where('project_tasks.id', intval($task_id));
            if ($trashed === false) {
                $builder->onlyTrashed();
            } elseif ($trashed === null) {
                $builder->withTrashed();
            }
            $task = $builder->first();
            // 仅"全员可见"(visibility=1)的任务走负责人只读视角；指定成员可见的任务交由 userTask 按可见性校验
            if (!empty($task) && intval($task->visibility) === 1 && UserDepartment::isDepartmentReadonlyProject($departmentView, intval($task->project_id))) {
                if ($archived === true && $task->archived_at != null) {
                    throw new ApiException('任务已归档', ['task_id' => $task_id]);
                }
                if ($archived === false && $task->archived_at == null) {
                    throw new ApiException('任务未归档', ['task_id' => $task_id]);
                }
                return $task;
            }
        }
        return self::userTask($task_id, $archived, $trashed, $with);
    }

    /**
     * 构建指定周期内的未完成任务查询（用于周报/日报等）
     * @param int $userid
     * @param Carbon $start_time
     * @param Carbon $end_time
     * @param bool $includeUpdatedForNoPlan 无计划时间任务是否按周期内更新时间一并纳入
     * @return \Illuminate\Database\Eloquent\Builder
     */
    public static function buildUnfinishedTaskQuery(int $userid, Carbon $start_time, Carbon $end_time, bool $includeUpdatedForNoPlan = true)
    {
        return self::query()
            ->join("projects", "projects.id", "=", "project_tasks.project_id")
            ->whereNull("projects.archived_at")
            ->whereNull("project_tasks.complete_at")
            ->whereHas("taskUser", function ($query) use ($userid) {
                $query->where("userid", $userid);
            })
            ->where(function ($query) use ($start_time, $end_time, $includeUpdatedForNoPlan) {
                // 1) 有计划时间：计划时间与给定周期 [start_time, end_time] 有交集
                $query->where(function ($q1) use ($start_time, $end_time) {
                    $q1->whereNotNull('project_tasks.start_at')
                        ->whereNotNull('project_tasks.end_at')
                        ->where(function ($q2) use ($start_time, $end_time) {
                            $q2->whereBetween('project_tasks.start_at', [$start_time->toDateTimeString(), $end_time->toDateTimeString()])
                                ->orWhereBetween('project_tasks.end_at', [$start_time->toDateTimeString(), $end_time->toDateTimeString()])
                                ->orWhere(function ($q3) use ($start_time, $end_time) {
                                    $q3->where('project_tasks.start_at', '<=', $start_time->toDateTimeString())
                                        ->where('project_tasks.end_at', '>=', $end_time->toDateTimeString());
                                });
                        });
                });
                // 2) 无计划时间
                $query->orWhere(function ($q1) use ($start_time, $end_time, $includeUpdatedForNoPlan) {
                    $q1->whereNull('project_tasks.start_at')
                        ->whereNull('project_tasks.end_at')
                        ->where(function ($q2) use ($start_time, $end_time, $includeUpdatedForNoPlan) {
                            $q2->whereBetween('project_tasks.created_at', [$start_time->toDateTimeString(), $end_time->toDateTimeString()]);
                            if ($includeUpdatedForNoPlan) {
                                $q2->orWhereBetween('project_tasks.updated_at', [$start_time->toDateTimeString(), $end_time->toDateTimeString()]);
                            }
                        });
                });
            })
            ->select("project_tasks.*")
            ->orderByDesc("project_tasks.id");
    }

    /**
     * 判断工作流名称是否为取消态（多语言）
     * @param string|null $flowItemName
     * @return bool
     */
    public static function isCanceledFlowName(?string $flowItemName): bool
    {
        if (empty($flowItemName)) {
            return false;
        }
        return preg_match('/已取消|Cancelled|취소됨|キャンセル済み|Abgebrochen|Annulé|Dibatalkan|Отменено/', $flowItemName) === 1;
    }
}
