package com.workbench.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.project.service.ProjectAccess;
import com.workbench.task.entity.Task;
import com.workbench.task.mapper.TaskMapper;
import com.workbench.task.web.BatchDtos.BatchRequest;
import com.workbench.task.web.BatchDtos.BatchResult;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 任务批量操作与 CSV 导出。 */
@Service
public class BatchService {

    private final TaskMapper taskMapper;
    private final ProjectAccess projectAccess;

    public BatchService(TaskMapper taskMapper, ProjectAccess projectAccess) {
        this.taskMapper = taskMapper;
        this.projectAccess = projectAccess;
    }

    @Transactional
    public BatchResult batch(long projectId, long currentUserid, BatchRequest request) {
        projectAccess.requireWrite(projectId, currentUserid);
        if (request.taskIds() == null || request.taskIds().isEmpty()) {
            throw new BusinessException("请选择任务");
        }
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .in(Task::getId, request.taskIds()));
        if (tasks.isEmpty()) {
            throw new BusinessException("任务不存在或不属于该项目");
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = 0;
        switch (request.action()) {
            case "complete" -> {
                for (Task task : tasks) {
                    taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                            .eq(Task::getId, task.getId())
                            .set(Task::getCompleteAt, now)
                            .set(Task::getUpdatedAt, now));
                    affected++;
                }
            }
            case "reopen" -> {
                for (Task task : tasks) {
                    taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                            .eq(Task::getId, task.getId())
                            .set(Task::getCompleteAt, null)
                            .set(Task::getUpdatedAt, now));
                    affected++;
                }
            }
            case "delete" -> {
                for (Task task : tasks) {
                    taskMapper.deleteById(task.getId());
                    affected++;
                }
            }
            case "assign" -> {
                if (request.ownerUserid() == null) {
                    throw new BusinessException("请选择负责人");
                }
                for (Task task : tasks) {
                    taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                            .eq(Task::getId, task.getId())
                            .set(Task::getOwnerUserid, request.ownerUserid())
                            .set(Task::getUpdatedAt, now));
                    affected++;
                }
            }
            default -> throw new BusinessException("不支持的操作：" + request.action());
        }
        return new BatchResult(affected, request.action());
    }

    /** 导出任务 CSV（含 BOM，Excel 打开不乱码）。 */
    public String exportTasksCsv(long projectId, long currentUserid) {
        projectAccess.requireAccess(projectId, currentUserid);
        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByAsc(Task::getId));
        StringBuilder csv = new StringBuilder();
        csv.append("id,name,owner_userid,p_level,start_at,end_at,complete_at\n");
        for (Task task : tasks) {
            csv.append(task.getId()).append(',')
                    .append(escape(task.getName())).append(',')
                    .append(task.getOwnerUserid() == null ? 0 : task.getOwnerUserid()).append(',')
                    .append(task.getPLevel() == null ? 0 : task.getPLevel()).append(',')
                    .append(task.getStartAt() == null ? "" : task.getStartAt()).append(',')
                    .append(task.getEndAt() == null ? "" : task.getEndAt()).append(',')
                    .append(task.getCompleteAt() == null ? "" : task.getCompleteAt())
                    .append('\n');
        }
        return csv.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\"", "\"\"");
        if (text.contains(",") || text.contains("\n") || text.contains("\"")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
