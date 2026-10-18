<?php

namespace App\Http\Controllers\Api;

use App\Module\Base;
use App\Module\ProjectMilestoneService;
use App\Models\User;
use Request;

class ProjectMilestoneController extends AbstractController
{
    /**
     * @api {get} api/projectmilestone/lists 获取项目里程碑
     * @apiGroup projectmilestone
     * @apiParam {Number} project_id 项目ID
     * @apiSuccess {Object[]} data.milestones 里程碑列表（包含任务数、完成数、逾期数、完成率和工作流状态分布；负责人/管理员另可查看提醒尝试次数、最近尝试结果和成功时间）
     * @apiSuccess {Boolean} data.can_manage 当前用户是否可维护里程碑
     */
    public function lists()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        if ($projectId <= 0) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('success', ProjectMilestoneService::lists($projectId));
    }

    /**
     * @api {post} api/projectmilestone/reminderattempts 查询里程碑提醒尝试记录
     * @apiGroup projectmilestone
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} milestone_id 里程碑ID
     * @apiParam {Number} [page=1] 页码
     * @apiParam {Number} [per_page=20] 每页数量，最大100
     * @apiSuccess {Object[]} data.items 最新提醒尝试结果（每个里程碑保留最近100条；仅项目负责人或管理员可见）
     */
    public function reminderAttempts()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $projectId = (int)Request::input('project_id');
        $milestoneId = (int)Request::input('milestone_id');
        $page = filter_var(Request::input('page', 1), FILTER_VALIDATE_INT);
        $perPage = filter_var(Request::input('per_page', 20), FILTER_VALIDATE_INT);
        if ($projectId <= 0 || $milestoneId <= 0 || $page === false || $perPage === false
            || $page <= 0 || $perPage <= 0 || $perPage > 100) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('success', ProjectMilestoneService::reminderAttempts($projectId, $milestoneId, $page, $perPage));
    }

    /**
     * @api {post} api/projectmilestone/tasks 获取里程碑关联任务
     * @apiGroup projectmilestone
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} milestone_id 里程碑ID
     * @apiParam {Number} [page=1] 页码
     * @apiParam {Number} [per_page=30] 每页数量，最大100
     * @apiSuccess {Object[]} data.list 当前用户可见的未归档任务
     * @apiSuccess {Number} data.total 任务总数
     */
    public function tasks()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $projectId = (int)Request::input('project_id');
        $milestoneId = (int)Request::input('milestone_id');
        $page = filter_var(Request::input('page', 1), FILTER_VALIDATE_INT);
        $perPage = filter_var(Request::input('per_page', 30), FILTER_VALIDATE_INT);
        if ($projectId <= 0 || $milestoneId <= 0 || $page === false || $perPage === false || $page <= 0 || $perPage <= 0) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('success', ProjectMilestoneService::tasks($projectId, $milestoneId, $page, $perPage));
    }

    /**
     * @api {post} api/projectmilestone/save 创建或更新里程碑
     * @apiGroup projectmilestone
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} [milestone_id] 里程碑ID；省略或0时创建
     * @apiParam {String} name 名称（1至100个字符）
     * @apiParam {String} [description] 说明（最多500个字符）
     * @apiParam {Number} [owner_userid] 项目成员ID；0表示未指定，未传时保留旧值
     * @apiParam {String} [target_at] 目标日期（YYYY-MM-DD）
     * @apiParam {String="open","completed"} [status] 状态
     * @apiParam {Number} [reminder_days=-1] 提醒提前天数：-1关闭，0当天，支持1/3/7天
     */
    public function save()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $projectId = (int)Request::input('project_id');
        $milestoneId = (int)Request::input('milestone_id', 0);
        $name = Request::input('name', '');
        $description = Request::input('description', '');
        $targetAt = Request::input('target_at');
        $status = Request::input('status', 'open');
        $reminderDays = Request::input('reminder_days', -1);
        $ownerUserid = Request::input('owner_userid');
        if ($projectId <= 0 || $milestoneId < 0 || !is_string($name) || !is_string($description)
            || !is_string($status) || ($targetAt !== null && !is_string($targetAt))
            || filter_var($reminderDays, FILTER_VALIDATE_INT) === false
            || (Request::has('owner_userid') && (filter_var($ownerUserid, FILTER_VALIDATE_INT) === false || (int)$ownerUserid < 0))) {
            return Base::retError('参数错误');
        }

        $milestone = ProjectMilestoneService::save(
            $projectId,
            $milestoneId,
            $name,
            $description,
            $targetAt,
            $status,
            (int)$reminderDays,
            Request::has('owner_userid') ? (int)$ownerUserid : null
        );
        return Base::retSuccess('保存成功', ['id' => (int)$milestone->id]);
    }

    /**
     * @api {post} api/projectmilestone/delete 删除里程碑并解除关联
     * @apiGroup projectmilestone
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} milestone_id 里程碑ID
     */
    public function delete()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $projectId = (int)Request::input('project_id');
        $milestoneId = (int)Request::input('milestone_id');
        if ($projectId <= 0 || $milestoneId <= 0) {
            return Base::retError('参数错误');
        }
        ProjectMilestoneService::delete($projectId, $milestoneId);
        return Base::retSuccess('已删除里程碑');
    }

    /**
     * @api {post} api/projectmilestone/sort 调整项目里程碑顺序
     * @apiGroup projectmilestone
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number[]} milestones 按新顺序排列的完整里程碑ID列表
     */
    public function sort()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $projectId = (int)Request::input('project_id');
        $milestoneIds = Request::input('milestones');
        if ($projectId <= 0 || !is_array($milestoneIds)) {
            return Base::retError('参数错误');
        }
        foreach ($milestoneIds as $milestoneId) {
            if (filter_var($milestoneId, FILTER_VALIDATE_INT) === false || (int)$milestoneId <= 0) {
                return Base::retError('参数错误');
            }
        }

        ProjectMilestoneService::sort($projectId, array_map('intval', $milestoneIds));
        return Base::retSuccess('排序已保存');
    }

    /**
     * @api {post} api/projectmilestone/assign 关联任务与里程碑
     * @apiGroup projectmilestone
     * @apiParam {Number} task_id 任务ID
     * @apiParam {Number} [milestone_id] 里程碑ID；0时解除关联
     */
    public function assign()
    {
        User::auth();
        if (!Request::isMethod('post')) {
            return Base::retError('请求方式错误');
        }
        $taskId = (int)Request::input('task_id');
        $milestoneId = (int)Request::input('milestone_id', 0);
        if ($taskId <= 0 || $milestoneId < 0) {
            return Base::retError('参数错误');
        }
        ProjectMilestoneService::assign($taskId, $milestoneId);
        return Base::retSuccess('已更新任务里程碑');
    }
}
