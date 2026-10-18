<?php

namespace App\Http\Controllers\Api;

use App\Module\Base;
use App\Module\ProjectTaskViewService;
use App\Models\User;
use Request;

class ProjectTaskViewController extends AbstractController
{
    /**
     * @api {get} api/projecttaskview/lists 获取当前用户可见的任务视图
     * @apiGroup projecttaskview
     * @apiName lists
     * @apiParam {Number} project_id 项目ID
     */
    public function lists()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        if ($projectId <= 0) return Base::retError('参数错误');
        return Base::retSuccess('success', ProjectTaskViewService::lists($projectId));
    }

    /**
     * @api {post} api/projecttaskview/save 新建或更新任务视图
     * @apiGroup projecttaskview
     * @apiName save
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} [view_id] 视图ID，0表示新建
     * @apiParam {String} name 视图名称
     * @apiParam {String="personal","project"} visibility 个人或项目共享
     * @apiParam {Object} definition 筛选、计划日期范围、排序、表格分组和可见字段定义
     */
    public function save()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $viewId = (int)Request::input('view_id', 0);
        $name = Request::input('name', '');
        $visibility = Request::input('visibility', 'personal');
        $definition = Request::input('definition', []);
        if ($projectId <= 0 || $viewId < 0 || !is_string($name) || !is_string($visibility) || !is_array($definition)) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('保存成功', ProjectTaskViewService::save($projectId, $viewId, $name, $visibility, $definition));
    }

    /**
     * @api {post} api/projecttaskview/delete 删除任务视图
     * @apiGroup projecttaskview
     * @apiName delete
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} view_id 视图ID
     */
    public function delete()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $viewId = (int)Request::input('view_id');
        if ($projectId <= 0 || $viewId <= 0) return Base::retError('参数错误');
        ProjectTaskViewService::delete($projectId, $viewId);
        return Base::retSuccess('已删除视图');
    }

    /**
     * @api {post} api/projecttaskview/setdefault 设置当前用户的默认任务视图
     * @apiGroup projecttaskview
     * @apiName setdefault
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} view_id 视图ID
     */
    public function setdefault()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $viewId = (int)Request::input('view_id');
        if ($projectId <= 0 || $viewId <= 0) return Base::retError('参数错误');
        ProjectTaskViewService::setDefault($projectId, $viewId);
        return Base::retSuccess('已设为默认视图');
    }
}
