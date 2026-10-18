<?php

namespace App\Http\Controllers\Api;

use App\Models\Project;
use App\Models\ProjectMilestone;
use App\Models\ProjectTaskTemplate;
use App\Models\ProjectTaskTemplateRevision;
use App\Models\User;
use App\Module\Base;
use Illuminate\Support\Facades\DB;
use Request;

class ProjectTaskTemplateController extends AbstractController
{
    /**
     * @api {get} api/projecttasktemplate/revisions 查看任务模板版本历史
     * @apiGroup projecttasktemplate
     * @apiName revisions
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} template_id 模板ID
     * @apiParam {Number} [page=1] 历史版本页码
     * @apiParam {Number} [per_page=20] 每页数量，最大50
     */
    public function revisions()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        $templateId = (int)Request::input('template_id');
        if ($projectId <= 0 || $templateId <= 0) return Base::retError('参数错误');
        Project::userProject($projectId, true, true);
        $template = ProjectTaskTemplate::where('project_id', $projectId)->where('id', $templateId)->first();
        if (!$template) return Base::retError('模板不存在或已被删除');

        $page = filter_var(Request::input('page', 1), FILTER_VALIDATE_INT);
        $perPage = filter_var(Request::input('per_page', 20), FILTER_VALIDATE_INT);
        if ($page === false || $perPage === false || $page <= 0 || $perPage <= 0) {
            return Base::retError('参数错误');
        }
        $perPage = min(50, $perPage);
        $revisionQuery = ProjectTaskTemplateRevision::where('project_id', $projectId)
            ->where('template_id', $templateId);
        $total = (clone $revisionQuery)->count();
        $revisions = $revisionQuery->orderByDesc('version')->forPage($page, $perPage)->get();
        $userNames = User::whereIn('userid', $revisions->pluck('changed_by')->filter()->unique())
            ->pluck('nickname', 'userid');
        $revisions->transform(function ($revision) use ($userNames) {
            $revision->changed_by_name = $userNames[$revision->changed_by] ?? '';
            return $revision;
        });
        return Base::retSuccess('success', [
            'current' => [
                'version' => max(1, (int)$template->version),
                'name' => $template->name,
                'title' => $template->title,
                'content' => $template->content,
                'milestone_name' => $template->milestone_name,
                'estimated_minutes' => $template->estimated_minutes,
                'subtasks' => $template->subtasks ?: [],
                'updated_at' => $template->updated_at,
            ],
            'revisions' => $revisions,
            'total' => $total,
            'page' => $page,
            'per_page' => $perPage,
        ]);
    }

    /**
     * @api {post} api/projecttasktemplate/restore 恢复任务模板历史版本
     * @apiGroup projecttasktemplate
     * @apiName restore
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} template_id 模板ID
     * @apiParam {Number} version 要恢复的历史版本
     */
    public function restore()
    {
        $user = User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $templateId = (int)Request::input('template_id');
        $version = (int)Request::input('version');
        if ($projectId <= 0 || $templateId <= 0 || $version <= 0) return Base::retError('参数错误');
        $project = Project::userProject($projectId, true, true);

        $result = DB::transaction(function () use ($projectId, $templateId, $version, $user) {
            // Milestone edits/deletes use the project row as their serialization lock.
            // Lock it here so a snapshot cannot be restored against a changing milestone set.
            $lockedProject = Project::query()->whereKey($projectId)->lockForUpdate()->first();
            if (!$lockedProject) return null;
            Project::userProject($projectId, true, true);
            $template = ProjectTaskTemplate::where('project_id', $projectId)
                ->where('id', $templateId)
                ->lockForUpdate()
                ->first();
            if (!$template) return null;
            $revision = ProjectTaskTemplateRevision::where('project_id', $projectId)
                ->where('template_id', $templateId)
                ->where('version', $version)
                ->first();
            if (!$revision) return false;

            $milestoneError = self::revisionMilestoneError($projectId, $revision);
            if ($milestoneError) return ['error' => $milestoneError];

            $currentVersion = max(1, (int)$template->version);
            $sameContent = $template->name === $revision->name
                && $template->title === $revision->title
                && $template->content === $revision->content
                && $template->milestone_name === $revision->milestone_name
                && (int)$template->estimated_minutes === (int)$revision->estimated_minutes
                && ($template->subtasks ?: []) === ($revision->subtasks ?: []);
            if ($sameContent) return ['template' => $template, 'changed' => false];

            $templateName = $template->name;

            ProjectTaskTemplateRevision::firstOrCreate(
                ['template_id' => $template->id, 'version' => $currentVersion],
                [
                    'project_id' => $projectId,
                    'name' => $template->name,
                    'title' => $template->title,
                    'content' => $template->content,
                    'milestone_name' => $template->milestone_name,
                    'estimated_minutes' => $template->estimated_minutes,
                    'subtasks' => $template->subtasks,
                    'changed_by' => $user->userid,
                ]
            );
            $template->name = $revision->name;
            $template->title = $revision->title;
            $template->content = $revision->content;
            $template->milestone_name = $revision->milestone_name;
            $template->estimated_minutes = $revision->estimated_minutes;
            $template->subtasks = $revision->subtasks ?: [];
            $template->version = $currentVersion + 1;
            $template->save();
            return [
                'template' => $template,
                'changed' => true,
                'name' => $templateName,
                'from_version' => $currentVersion,
                'to_version' => (int)$template->version,
            ];
        });

        if ($result === null) return Base::retError('模板不存在或已被删除');
        if ($result === false) return Base::retError('历史版本不存在');
        if (isset($result['error'])) return Base::retError($result['error']);
        $template = $result['template'];
        if ($result['changed']) {
            $project->addLog('恢复任务模板历史版本', [
                'change' => [$result['name'], $result['from_version'], $result['to_version']],
            ], $user->userid);
            $project->pushMsg('taskTemplateUpdate', ['template_id' => (int)$template->id]);
        }
        return Base::retSuccess('恢复成功', $template);
    }

    private static function revisionMilestoneError(int $projectId, ProjectTaskTemplateRevision $revision): ?string
    {
        $milestoneNames = [];
        if (is_string($revision->milestone_name) && trim($revision->milestone_name) !== '') {
            $milestoneNames[] = trim($revision->milestone_name);
        }

        $collectSubtaskMilestones = function ($items) use (&$collectSubtaskMilestones, &$milestoneNames) {
            if (!is_array($items)) return;
            foreach ($items as $item) {
                if (!is_array($item)) continue;
                $name = $item['milestone_name'] ?? null;
                if (is_string($name) && trim($name) !== '') {
                    $milestoneNames[] = trim($name);
                }
                $collectSubtaskMilestones($item['children'] ?? []);
            }
        };
        $collectSubtaskMilestones($revision->subtasks ?: []);
        $milestoneNames = array_values(array_unique($milestoneNames));
        if (!$milestoneNames) return null;

        $milestones = ProjectMilestone::query()
            ->select('name', DB::raw('COUNT(*) AS milestone_count'))
            ->where('project_id', $projectId)
            ->whereIn('name', $milestoneNames)
            ->groupBy('name')
            ->get()
            ->keyBy('name');
        foreach ($milestoneNames as $name) {
            $milestone = $milestones->get($name);
            if (!$milestone) {
                return '历史模板引用的里程碑已不存在，请先恢复里程碑或编辑模板后再恢复';
            }
            if ((int)$milestone->milestone_count > 1) {
                return '项目存在重名里程碑，请先重命名后再恢复模板';
            }
        }
        return null;
    }
}
