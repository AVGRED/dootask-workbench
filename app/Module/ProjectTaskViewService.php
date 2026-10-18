<?php

namespace App\Module;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectMilestone;
use App\Models\ProjectTaskView;
use App\Models\ProjectTaskViewDefault;
use App\Models\User;
use Illuminate\Support\Facades\DB;

class ProjectTaskViewService
{
    public static function lists(int $projectId): array
    {
        $project = Project::findForDepartmentView($projectId);
        $userId = (int)User::userid();
        $defaultViewId = (int)ProjectTaskViewDefault::where('project_id', $project->id)
            ->where('userid', $userId)
            ->value('view_id');
        $canManageShared = $project->isOwner($userId);

        return ProjectTaskView::query()
            ->where('project_id', $project->id)
            ->where(function ($query) use ($userId) {
                $query->where('visibility', 'project')
                    ->orWhere(function ($personal) use ($userId) {
                        $personal->where('visibility', 'personal')->where('created_by', $userId);
                    });
            })
            ->orderBy('name')
            ->get()
            ->map(fn (ProjectTaskView $view) => self::serialize($view, $userId, $canManageShared, $defaultViewId))
            ->all();
    }

    public static function save(int $projectId, int $viewId, string $name, string $visibility, array $definition): array
    {
        Project::userProject($projectId);
        $userId = (int)User::userid();
        $name = trim($name);
        if ($name === '' || mb_strlen($name) > 100) {
            throw new ApiException('视图名称须为1至100个字符');
        }
        if (!in_array($visibility, ['personal', 'project'], true)) {
            throw new ApiException('视图范围无效');
        }
        $definition = self::normalizeDefinition($definition);
        [$project, $view] = DB::transaction(function () use ($projectId, $viewId, $name, $visibility, $definition, $userId) {
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            // Recheck membership and permissions after acquiring the same project lock used by
            // milestone changes, deletes, and default-view updates.
            $project = Project::userProject($projectId);
            if ($visibility === 'project' && !$project->isOwner($userId)) {
                throw new ApiException('仅项目负责人或管理员可创建共享视图');
            }

            $milestoneId = (int)($definition['milestone_id'] ?? 0);
            if ($milestoneId > 0 && !ProjectMilestone::query()
                ->where('project_id', $project->id)
                ->whereKey($milestoneId)
                ->exists()) {
                throw new ApiException('筛选里程碑不存在或不属于当前项目');
            }

            if ($viewId > 0) {
                $view = self::editableView($project, $viewId, $userId);
                if ($view->visibility === 'project' && $visibility === 'personal' && (int)$view->created_by !== $userId) {
                    throw new ApiException('仅共享视图创建人可将其改为个人视图');
                }
            } else {
                $view = ProjectTaskView::createInstance([
                    'project_id' => $project->id,
                    'created_by' => $userId,
                ]);
            }
            $view->name = $name;
            $view->visibility = $visibility;
            $view->definition = $definition;
            $view->save();

            if ($viewId > 0 && $view->wasChanged('visibility') && $visibility === 'personal') {
                ProjectTaskViewDefault::where('project_id', $project->id)
                    ->where('view_id', $view->id)
                    ->where('userid', '<>', $userId)
                    ->delete();
            }
            return [$project, $view];
        });

        $isDefault = (int)ProjectTaskViewDefault::where('project_id', $project->id)
            ->where('userid', $userId)
            ->value('view_id') === (int)$view->id;
        return self::serialize($view, $userId, $project->isOwner($userId), $isDefault ? (int)$view->id : 0);
    }

    public static function delete(int $projectId, int $viewId): void
    {
        Project::userProject($projectId);
        $userId = (int)User::userid();
        DB::transaction(function () use ($projectId, $viewId, $userId) {
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($projectId);
            $view = self::editableView($project, $viewId, $userId);
            ProjectTaskViewDefault::where('project_id', $project->id)
                ->where('view_id', $view->id)
                ->delete();
            $view->delete();
        });
    }

    public static function setDefault(int $projectId, int $viewId): void
    {
        Project::userProject($projectId);
        $userId = (int)User::userid();
        DB::transaction(function () use ($projectId, $viewId, $userId) {
            Project::query()->whereKey($projectId)->lockForUpdate()->firstOrFail();
            $project = Project::userProject($projectId);
            $view = self::visibleView($project, $viewId, $userId);
            ProjectTaskViewDefault::updateOrCreate(
                ['project_id' => $project->id, 'userid' => $userId],
                ['view_id' => $view->id]
            );
        });
    }

    private static function normalizeDefinition(array $definition): array
    {
        $flow = $definition['flow'] ?? [];
        if (!is_array($flow)) {
            throw new ApiException('筛选定义格式无效');
        }
        $flowKeys = ['flows', 'tags', 'users'];
        $normalizedFlow = [];
        foreach ($flowKeys as $key) {
            $values = $flow[$key] ?? [];
            if (!is_array($values) || count($values) > 500) {
                throw new ApiException('筛选定义格式无效');
            }
            foreach ($values as $value) {
                if (!is_scalar($value)) {
                    throw new ApiException('筛选定义格式无效');
                }
            }
            $values = $key === 'tags'
                ? array_map(fn ($value) => trim(mb_substr((string)$value, 0, 100)), $values)
                : array_map('intval', $values);
            $normalizedFlow[$key] = array_slice(array_values(array_unique(array_filter($values, fn ($value) => $value !== ''))), 0, 500);
        }
        $tab = $definition['tab'] ?? 'column';
        if (!in_array($tab, ['column', 'table', 'gantt'], true)) {
            $tab = 'column';
        }
        $sort = $definition['sort'] ?? [];
        if (!is_array($sort)) {
            $sort = [];
        }
        $sortField = in_array($sort['field'] ?? '', ['end_at', 'level'], true) ? $sort['field'] : 'end_at';
        $sortType = in_array($sort['type'] ?? '', ['asc', 'desc'], true) ? $sort['type'] : 'desc';
        $groups = $definition['table_groups'] ?? [];
        $validGroups = ['my', 'help', 'undone', 'completed', 'dueThisWeek', 'dueNextWeek', 'plannedThisWeek', 'plannedNextWeek', 'overdue', 'unscheduled'];
        if (!is_array($groups) || count($groups) > count($validGroups)) {
            throw new ApiException('筛选定义格式无效');
        }
        $groups = array_filter($groups, 'is_scalar');
        $keyword = $definition['keyword'] ?? '';
        if (!is_scalar($keyword)) {
            $keyword = '';
        }
        $milestoneId = $definition['milestone_id'] ?? 0;
        if (!is_scalar($milestoneId)) {
            $milestoneId = 0;
        }
        $dateRange = $definition['date_range'] ?? [];
        if (!is_array($dateRange)) {
            $dateRange = [];
        }
        $dateFrom = $dateRange['from'] ?? '';
        $dateTo = $dateRange['to'] ?? '';
        $dateRange = is_string($dateFrom) && is_string($dateTo)
            && self::validDate($dateFrom) && self::validDate($dateTo) && $dateFrom <= $dateTo
            ? ['from' => $dateFrom, 'to' => $dateTo]
            : [];
        $tableFields = $definition['table_fields'] ?? ['column', 'priority', 'owner', 'due'];
        if (!is_array($tableFields) || count($tableFields) > 5) {
            throw new ApiException('筛选定义格式无效');
        }
        $tableFields = array_values(array_intersect(
            ['column', 'priority', 'owner', 'due', 'estimate'],
            array_map('strval', array_filter($tableFields, 'is_scalar'))
        ));

        return [
            'version' => 1,
            'flow' => $normalizedFlow,
            'milestone_id' => max(0, (int)$milestoneId),
            'date_range' => $dateRange,
            'keyword' => trim(mb_substr((string)$keyword, 0, 100)),
            'completed' => filter_var($definition['completed'] ?? false, FILTER_VALIDATE_BOOLEAN),
            'tab' => $tab,
            'sort' => ['field' => $sortField, 'type' => $sortType],
            'table_groups' => array_values(array_intersect($validGroups, array_map('strval', $groups))),
            'table_fields' => $tableFields,
        ];
    }

    private static function validDate(string $value): bool
    {
        if (strlen($value) !== 10 || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $value)) {
            return false;
        }
        [$year, $month, $day] = array_map('intval', explode('-', $value));
        return checkdate($month, $day, $year);
    }

    private static function editableView(Project $project, int $viewId, int $userId): ProjectTaskView
    {
        $view = self::visibleView($project, $viewId, $userId);
        if ($view->visibility === 'project' && !$project->isOwner($userId)) {
            throw new ApiException('仅项目负责人或管理员可修改共享视图');
        }
        if ($view->visibility === 'personal' && (int)$view->created_by !== $userId) {
            throw new ApiException('无权修改此个人视图');
        }
        return $view;
    }

    private static function visibleView(Project $project, int $viewId, int $userId): ProjectTaskView
    {
        $view = ProjectTaskView::where('project_id', $project->id)->whereKey($viewId)->first();
        if (!$view || ($view->visibility === 'personal' && (int)$view->created_by !== $userId)) {
            throw new ApiException('视图不存在或无权访问');
        }
        return $view;
    }

    private static function serialize(ProjectTaskView $view, int $userId, bool $canManageShared, int $defaultViewId): array
    {
        return [
            'id' => (int)$view->id,
            'name' => $view->name,
            'visibility' => $view->visibility,
            'definition' => $view->definition ?: [],
            'is_default' => (int)$view->id === $defaultViewId,
            'can_edit' => $view->visibility === 'personal'
                ? (int)$view->created_by === $userId
                : $canManageShared,
            'created_by' => (int)$view->created_by,
        ];
    }
}
