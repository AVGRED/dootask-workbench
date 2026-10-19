<?php

namespace App\Http\Controllers\Api;

use App\Module\Base;
use App\Module\ProjectTaskWorkService;
use App\Models\User;
use Request;

class ProjectTaskWorkController extends AbstractController
{
    /** @api {post} api/projecttaskwork/estimate 更新任务预估工时（分钟） */
    public function estimate()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        $taskId = (int)Request::input('task_id');
        if ($projectId <= 0 || $taskId <= 0) return Base::retError('参数错误');
        return Base::retSuccess('保存成功', ProjectTaskWorkService::setEstimate($projectId, $taskId, Request::input('estimated_minutes')));
    }

    /**
     * @api {get} api/projecttaskwork/task 查询任务工时记录
     * @apiParam {Number} [page=1] 工时历史页码
     * @apiParam {Number} [per_page=50] 每页记录数，最大100
     * @apiSuccess {String} data.work_date_today 项目排期时区的当前日期
     * @apiSuccess {Boolean} data.can_edit 当前用户是否可以修改估时或登记工时
     */
    public function task()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        $taskId = (int)Request::input('task_id');
        if ($projectId <= 0 || $taskId <= 0) return Base::retError('参数错误');
        $page = filter_var(Request::input('page', 1), FILTER_VALIDATE_INT);
        $perPage = filter_var(Request::input('per_page', 50), FILTER_VALIDATE_INT);
        if ($page === false || $perPage === false || $page <= 0 || $perPage <= 0 || $perPage > 100) return Base::retError('参数错误');
        $logPage = ProjectTaskWorkService::logs($projectId, $taskId, $page, $perPage);
        return Base::retSuccess('success', [
            'logs' => $logPage['items'],
            'logs_total' => $logPage['total'],
            'logs_page' => $logPage['page'],
            'logs_per_page' => $logPage['per_page'],
            'summary' => ProjectTaskWorkService::taskSummary($projectId, $taskId),
            'work_date_today' => ProjectTaskWorkService::projectToday($projectId),
            'can_edit' => ProjectTaskWorkService::canEditWork($projectId, $taskId),
        ]);
    }

    /**
     * @api {post} api/projecttaskwork/add 登记实际工时
     * @apiParam {String} [work_date] 工作日期（YYYY-MM-DD，项目排期时区；省略时取项目当地日期）
     */
    public function add()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $taskId = (int)Request::input('task_id');
        if ($projectId <= 0 || $taskId <= 0) return Base::retError('参数错误');
        return Base::retSuccess('工时已登记', ProjectTaskWorkService::addLog($projectId, $taskId, Request::only(['request_id', 'minutes', 'work_date', 'note'])));
    }

    /** @api {post} api/projecttaskwork/delete 删除本人或管理员的工时记录 */
    public function delete()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $logId = (int)Request::input('log_id');
        if ($projectId <= 0 || $logId <= 0) return Base::retError('参数错误');
        return Base::retSuccess('工时记录已删除', ProjectTaskWorkService::deleteLog($projectId, $logId));
    }

    /**
     * @api {get} api/projecttaskwork/capacity 按成员汇总计划与实际工时
     * @apiParam {Number} project_id 项目ID
     * @apiParam {String} from 起始日期（YYYY-MM-DD，项目排期时区）
     * @apiParam {String} to 结束日期（YYYY-MM-DD，项目排期时区，最多93天）
     * @apiSuccess {Array} data.members 项目成员的计划、实际和可用工时；可用容量按配置工作日每天8小时计算
     */
    public function capacity()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        $from = Request::input('from');
        $to = Request::input('to');
        if ($projectId <= 0 || !is_string($from) || !is_string($to)) return Base::retError('参数错误');
        return Base::retSuccess('success', ProjectTaskWorkService::capacity($projectId, $from, $to));
    }
}
