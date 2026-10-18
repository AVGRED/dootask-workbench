<?php

namespace App\Http\Controllers\Api;

use App\Module\Base;
use App\Module\ProjectTaskDependencyService;
use App\Models\User;
use Request;

/**
 * Task dependency APIs.
 */
class ProjectTaskDependencyController extends AbstractController
{
    /**
     * @api {get} api/projecttaskdependency/lists 获取任务前置与后置依赖（项目关闭依赖时返回空列表）
     * @apiGroup projecttaskdependency
     * @apiParam {Number} task_id 任务ID
     */
    public function lists()
    {
        User::auth();
        $taskId = (int)Request::input('task_id');
        if ($taskId <= 0) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('success', ProjectTaskDependencyService::lists($taskId));
    }

    /**
     * @api {get} api/projecttaskdependency/project 获取甘特图中的任务依赖
     * @apiGroup projecttaskdependency
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Array} task_ids 当前甘特图任务ID，最多5000个；接口会返回其权限可见的直接依赖
     * @apiSuccess {Object[]} data.list 前置任务和后置任务边，含两端任务的可见排期摘要
     */
    public function project()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        $taskIds = Request::input('task_ids', []);
        if ($projectId <= 0 || !is_array($taskIds)) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('success', [
            'list' => ProjectTaskDependencyService::projectLists($projectId, $taskIds),
        ]);
    }

    /**
     * @api {get} api/projecttaskdependency/risks 查询当前用户可见的项目依赖风险
     * @apiGroup projecttaskdependency
     * @apiParam {Number} project_id 项目ID
     * @apiSuccess {Number} data.schedule_conflicts 未完成后续任务计划开始早于前置任务计划结束的数量
     * @apiSuccess {Number} data.overdue_blockers 前置任务未完成且计划结束时间已过、后续任务仍未完成的数量
     * @apiSuccess {Object[]} data.items 风险边摘要，最多返回50条；两端任务都必须对当前用户可见
     */
    public function risks()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        if ($projectId <= 0) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('success', ProjectTaskDependencyService::riskSummary($projectId));
    }

    /**
     * @api {post} api/projecttaskdependency/add 添加任务依赖（项目关闭依赖时拒绝写入）
     * @apiGroup projecttaskdependency
     * @apiParam {Number} task_id 当前任务ID
     * @apiParam {Number} related_task_id 关联任务ID
     * @apiParam {String="predecessor","successor"} direction 关联方向
     */
    public function add()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $taskId = (int)Request::input('task_id');
        $relatedTaskId = (int)Request::input('related_task_id');
        $direction = (string)Request::input('direction');
        $dependency = ProjectTaskDependencyService::add($taskId, $relatedTaskId, $direction);
        return Base::retSuccess('依赖已添加', ['id' => $dependency->id]);
    }

    /**
     * @api {post} api/projecttaskdependency/delete 删除任务依赖
     * @apiGroup projecttaskdependency
     * @apiParam {Number} task_id 当前任务ID
     * @apiParam {Number} dependency_id 依赖关系ID
     */
    public function delete()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $taskId = (int)Request::input('task_id');
        $dependencyId = (int)Request::input('dependency_id');
        if ($taskId <= 0 || $dependencyId <= 0) {
            return Base::retError('参数错误');
        }
        ProjectTaskDependencyService::delete($taskId, $dependencyId);
        return Base::retSuccess('依赖已移除');
    }
}
