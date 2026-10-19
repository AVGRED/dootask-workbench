package com.workbench.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbench.common.api.PageResult;
import com.workbench.project.entity.ActivityLog;
import com.workbench.project.mapper.ActivityLogMapper;
import com.workbench.project.web.ActivityDtos.ActivityView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 项目动态：记录谁在什么时候改了什么，供动态面板与后续统计使用。
 *
 * <p>埋点失败不影响主流程，因此 {@link #record} 内部吞掉异常只记日志。</p>
 */
@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private static final Map<String, String> ACTION_TEXT = Map.ofEntries(
            Map.entry("project.create", "创建项目"),
            Map.entry("project.archive", "归档项目"),
            Map.entry("project.restore", "恢复项目"),
            Map.entry("task.create", "创建任务"),
            Map.entry("task.update", "更新任务"),
            Map.entry("task.complete", "完成任务"),
            Map.entry("task.reopen", "取消完成"),
            Map.entry("task.delete", "删除任务"),
            Map.entry("milestone.create", "创建里程碑"),
            Map.entry("milestone.delete", "删除里程碑"));

    private final ActivityLogMapper activityLogMapper;

    public ActivityService(ActivityLogMapper activityLogMapper) {
        this.activityLogMapper = activityLogMapper;
    }

    /** 写一条动态；异常只记录日志，不向上抛。 */
    public void record(long projectId, Long taskId, long userid, String action,
                       String targetType, Long targetId, String detail) {
        try {
            ActivityLog entry = new ActivityLog();
            entry.setProjectId(projectId);
            entry.setTaskId(taskId);
            entry.setUserid(userid);
            entry.setAction(action);
            entry.setTargetType(targetType == null ? "" : targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail == null ? "" : detail.length() > 2000 ? detail.substring(0, 2000) : detail);
            entry.setCreatedAt(LocalDateTime.now());
            activityLogMapper.insert(entry);
        } catch (RuntimeException exception) {
            log.warn("failed to record activity {} for project {}", action, projectId, exception);
        }
    }

    public PageResult<ActivityView> page(long projectId, Long taskId, int page, int size) {
        LambdaQueryWrapper<ActivityLog> wrapper = new LambdaQueryWrapper<ActivityLog>()
                .eq(ActivityLog::getProjectId, projectId)
                .orderByDesc(ActivityLog::getId);
        if (taskId != null && taskId > 0) {
            wrapper.eq(ActivityLog::getTaskId, taskId);
        }
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<ActivityLog> result = activityLogMapper.selectPage(new Page<>(Math.max(page, 1), safeSize), wrapper);
        List<ActivityView> list = result.getRecords().stream().map(ActivityService::toView).toList();
        return PageResult.of(list, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    public static String textOf(String action) {
        return ACTION_TEXT.getOrDefault(action, action);
    }

    private static ActivityView toView(ActivityLog entry) {
        return new ActivityView(
                entry.getId(),
                entry.getTaskId(),
                entry.getUserid(),
                entry.getAction(),
                textOf(entry.getAction()),
                entry.getDetail(),
                entry.getCreatedAt());
    }
}
