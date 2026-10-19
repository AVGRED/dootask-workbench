package com.workbench.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbench.common.api.BusinessException;
import com.workbench.common.api.PageResult;
import com.workbench.project.entity.Project;
import com.workbench.project.entity.ProjectMember;
import com.workbench.project.mapper.ProjectMapper;
import com.workbench.project.mapper.ProjectMemberMapper;
import com.workbench.task.entity.Task;
import com.workbench.task.mapper.TaskMapper;
import com.workbench.task.web.TaskDtos.SaveTaskRequest;
import com.workbench.task.web.TaskDtos.TaskView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 任务：按项目分页、创建、更新、完成/取消完成、软删。 */
@Service
public class TaskService {

    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final JdbcTemplate jdbcTemplate;

    public TaskService(TaskMapper taskMapper, ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                       JdbcTemplate jdbcTemplate) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<TaskView> page(long currentUserid, long projectId, int page, int size, String keyword,
                                     Long ownerUserid, Long milestoneId) {
        requireAccess(currentUserid, projectId);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .isNull(Task::getCompleteAt)
                .orderByAsc(Task::getSort)
                .orderByDesc(Task::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Task::getName, keyword);
        }
        if (ownerUserid != null) {
            wrapper.eq(Task::getOwnerUserid, ownerUserid);
        }
        if (milestoneId != null && milestoneId > 0) {
            wrapper.eq(Task::getMilestoneId, milestoneId);
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Task> result = taskMapper.selectPage(new Page<>(Math.max(page, 1), safeSize), wrapper);
        List<TaskView> list = result.getRecords().stream().map(TaskService::toView).toList();
        return PageResult.of(list, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    public TaskView create(long currentUserid, long projectId, SaveTaskRequest request) {
        requireAccess(currentUserid, projectId);
        Long milestoneId = resolveMilestone(projectId, request.milestoneId());
        Task task = new Task();
        task.setProjectId(projectId);
        task.setMilestoneId(milestoneId);
        task.setName(request.name());
        task.setDesc(request.desc() == null ? "" : request.desc());
        task.setOwnerUserid(request.ownerUserid() == null ? 0L : request.ownerUserid());
        task.setStartAt(request.startAt());
        task.setEndAt(request.endAt());
        task.setPLevel(request.pLevel() == null ? 0 : request.pLevel());
        task.setUserid(currentUserid);
        task.setParentId(0L);
        task.setColumnId(0L);
        task.setDialogId(0L);
        task.setColor("");
        task.setArchivedUserid(0L);
        task.setSort(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return toView(task);
    }

    public TaskView update(long currentUserid, long taskId, SaveTaskRequest request) {
        Task task = requireTask(taskId);
        requireAccess(currentUserid, Objects.requireNonNull(task.getProjectId()));
        task.setName(request.name());
        if (request.desc() != null) task.setDesc(request.desc());
        if (request.ownerUserid() != null) task.setOwnerUserid(request.ownerUserid());
        if (request.startAt() != null) task.setStartAt(request.startAt());
        if (request.endAt() != null) task.setEndAt(request.endAt());
        if (request.pLevel() != null) task.setPLevel(request.pLevel());
        // milestoneId = 0 表示清除关联；null 表示不改动。
        if (request.milestoneId() != null) {
            task.setMilestoneId(resolveMilestone(Objects.requireNonNull(task.getProjectId()), request.milestoneId()));
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        if (request.milestoneId() != null && request.milestoneId() == 0L) {
            // updateById 忽略 null 字段，清除里程碑要用 UpdateWrapper 显式置空。
            taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .set(Task::getMilestoneId, null));
            task.setMilestoneId(null);
        }
        return toView(task);
    }

    /** 校验里程碑存在且属于该项目；0 或 null 视为不关联。 */
    private Long resolveMilestone(long projectId, Long milestoneId) {
        if (milestoneId == null || milestoneId == 0L) {
            return null;
        }
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from project_milestones where id = ? and project_id = ?",
                Long.class, milestoneId, projectId);
        if (count == null || count == 0) {
            throw new BusinessException("里程碑不存在或不属于该项目");
        }
        return milestoneId;
    }

    public void complete(long currentUserid, long taskId, boolean completed) {
        Task task = requireTask(taskId);
        requireAccess(currentUserid, Objects.requireNonNull(task.getProjectId()));
        LocalDateTime now = LocalDateTime.now();
        if (completed) {
            task.setCompleteAt(now);
            task.setUpdatedAt(now);
            taskMapper.updateById(task);
        } else {
            // updateById 会忽略 null 字段，取消完成必须用 UpdateWrapper 显式置空。
            taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .set(Task::getCompleteAt, null)
                    .set(Task::getUpdatedAt, now));
        }
    }

    public void delete(long currentUserid, long taskId) {
        Task task = requireTask(taskId);
        requireAccess(currentUserid, Objects.requireNonNull(task.getProjectId()));
        taskMapper.deleteById(taskId);
    }

    private Task requireTask(long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    /** 负责人或成员可访问；非成员一律拒绝。 */
    private void requireAccess(long currentUserid, long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (Objects.equals(project.getUserid(), currentUserid)) {
            return;
        }
        Long member = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserid, currentUserid));
        if (member == null || member == 0) {
            throw new BusinessException("无权访问该项目");
        }
    }

    private static TaskView toView(Task task) {
        return new TaskView(
                task.getId(),
                task.getProjectId(),
                task.getName(),
                task.getDesc(),
                task.getOwnerUserid() == null ? 0L : task.getOwnerUserid(),
                task.getStartAt(),
                task.getEndAt(),
                task.getCompleteAt(),
                task.getPLevel() == null ? 0 : task.getPLevel(),
                task.getCompleteAt() != null,
                task.getMilestoneId());
    }
}
