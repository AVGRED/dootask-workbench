<?php

namespace App\Http\Controllers\Api;

use App\Module\Base;
use App\Module\ProjectTaskScheduleService;
use App\Models\User;
use Request;

class ProjectTaskScheduleController extends AbstractController
{
    /** @api {get} api/projecttaskschedule/batches 查询最近的排期批次 */
    public function batches()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        if ($projectId <= 0) return Base::retError('参数错误');
        return Base::retSuccess('success', ProjectTaskScheduleService::batches($projectId));
    }

    /** @api {get} api/projecttaskschedule/settings 读取项目排期规则 */
    public function settings()
    {
        User::auth();
        $projectId = (int)Request::input('project_id');
        if ($projectId <= 0) return Base::retError('参数错误');
        return Base::retSuccess('success', ProjectTaskScheduleService::settings($projectId));
    }

    /**
     * @api {post} api/projecttaskschedule/updatesettings 更新项目排期规则（负责人/管理员）
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Object} settings 完整排期规则（mode、workdays、timezone、buffer_days）
     * @apiParam {Array} [settings.holidays] 非工作日例外（YYYY-MM-DD，最多366个；省略时保留现有值）
     */
    public function updatesettings()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $settings = Request::input('settings', []);
        if ($projectId <= 0 || !is_array($settings)) return Base::retError('参数错误');
        return Base::retSuccess('保存成功', ProjectTaskScheduleService::updateSettings($projectId, $settings));
    }

    /**
     * @api {post} api/projecttaskschedule/preview 预演前置依赖变化后的级联排期
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} task_id 作为排期起点的主任务ID
     * @apiParam {String} start_at 新开始时间（YYYY-MM-DD HH:mm:ss，项目时区）
     * @apiParam {String} end_at 新结束时间（YYYY-MM-DD HH:mm:ss，项目时区）
     */
    public function preview()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $taskId = (int)Request::input('task_id');
        $startAt = Request::input('start_at');
        $endAt = Request::input('end_at');
        if ($projectId <= 0 || $taskId <= 0 || !is_string($startAt) || !is_string($endAt)) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('预演完成', ProjectTaskScheduleService::preview($projectId, $taskId, $startAt, $endAt));
    }

    /**
     * @api {post} api/projecttaskschedule/apply 按预演结果幂等应用排期批次
     * @apiParam {Number} project_id 项目ID
     * @apiParam {Number} task_id 作为排期起点的主任务ID
     * @apiParam {String} start_at 新开始时间（YYYY-MM-DD HH:mm:ss，项目时区）
     * @apiParam {String} end_at 新结束时间（YYYY-MM-DD HH:mm:ss，项目时区）
     * @apiParam {String} preview_hash 预演返回的 SHA-256 快照摘要
     * @apiParam {String} request_id 幂等请求编号，重试时复用；同一编号必须使用完全相同的项目、任务、起止时间和预演摘要
     */
    public function apply()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $taskId = (int)Request::input('task_id');
        $startAt = Request::input('start_at');
        $endAt = Request::input('end_at');
        $previewHash = Request::input('preview_hash');
        $requestId = Request::input('request_id');
        if ($projectId <= 0 || $taskId <= 0 || !is_string($startAt) || !is_string($endAt)
            || !is_string($previewHash) || !is_string($requestId)) {
            return Base::retError('参数错误');
        }
        return Base::retSuccess('排期已应用', ProjectTaskScheduleService::apply(
            $projectId,
            $taskId,
            $startAt,
            $endAt,
            $previewHash,
            $requestId
        ));
    }

    /** @api {post} api/projecttaskschedule/undo 撤销排期批次（仅当任务日期未被后续修改） */
    public function undo()
    {
        User::auth();
        if (!Request::isMethod('post')) return Base::retError('请求方式错误');
        $projectId = (int)Request::input('project_id');
        $batchId = (int)Request::input('batch_id');
        if ($projectId <= 0 || $batchId <= 0) return Base::retError('参数错误');
        return Base::retSuccess('排期批次已撤销', ProjectTaskScheduleService::undo($projectId, $batchId));
    }
}
