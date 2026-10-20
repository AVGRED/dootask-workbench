package com.workbench.worklog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.common.csv.Csv;
import com.workbench.project.service.ProjectAccess;
import com.workbench.worklog.capacity.CapacityCalculator;
import com.workbench.worklog.capacity.CapacityCalculator.LogRow;
import com.workbench.worklog.entity.WorkLog;
import com.workbench.worklog.mapper.WorkLogMapper;
import com.workbench.worklog.web.WorklogDtos.CapacitySummary;
import com.workbench.worklog.web.WorklogDtos.CreateWorklogRequest;
import com.workbench.worklog.web.WorklogDtos.WorklogView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 工时登记、删除、列表与容量汇总。
 *
 * <p>{@code request_id} 与 {@code (project, user, request)} 唯一键共同保证幂等：同一请求号重复提交
 * 返回同一条记录，不会重复计工时。</p>
 */
@Service
public class WorklogService {

    private static final int MAX_RANGE_DAYS = 93;

    private final WorkLogMapper workLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccess projectAccess;

    public WorklogService(WorkLogMapper workLogMapper, JdbcTemplate jdbcTemplate, ProjectAccess projectAccess) {
        this.workLogMapper = workLogMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.projectAccess = projectAccess;
    }

    public WorklogView create(long currentUserid, long taskId, CreateWorklogRequest request) {
        Long projectId = jdbcTemplate.query(
                "select project_id from project_tasks where id = ? and deleted_at is null",
                resultSet -> resultSet.next() ? resultSet.getLong("project_id") : null,
                taskId);
        if (projectId == null || projectId == 0) {
            throw new BusinessException("任务不存在");
        }
        projectAccess.requireWrite(projectId, currentUserid);

        WorkLog existing = workLogMapper.selectOne(new LambdaQueryWrapper<WorkLog>()
                .eq(WorkLog::getProjectId, projectId)
                .eq(WorkLog::getUserid, currentUserid)
                .eq(WorkLog::getRequestId, request.requestId())
                .last("limit 1"));
        if (existing != null) {
            return toView(existing);
        }

        WorkLog log = new WorkLog();
        log.setProjectId(projectId);
        log.setTaskId(taskId);
        log.setUserid(currentUserid);
        log.setRequestId(request.requestId());
        log.setRequestPayloadHash(payloadHash(request));
        log.setMinutes(request.minutes());
        log.setWorkDate(request.workDate());
        log.setNote(request.note() == null ? "" : request.note());
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        workLogMapper.insert(log);
        return toView(log);
    }

    public List<WorklogView> listByTask(long taskId, long currentUserid) {
        requireTaskAccess(taskId, currentUserid);
        return workLogMapper.selectList(new LambdaQueryWrapper<WorkLog>()
                        .eq(WorkLog::getTaskId, taskId)
                        .orderByDesc(WorkLog::getWorkDate)
                        .orderByDesc(WorkLog::getId))
                .stream()
                .map(WorklogService::toView)
                .toList();
    }

    public void delete(long currentUserid, long logId) {
        WorkLog log = workLogMapper.selectById(logId);
        if (log == null) {
            throw new BusinessException("工时记录不存在");
        }
        boolean self = Objects.equals(log.getUserid(), currentUserid);
        boolean projectOwner = false;
        if (!self) {
            Long owner = jdbcTemplate.query(
                    "select userid from projects where id = ? and deleted_at is null",
                    resultSet -> resultSet.next() ? resultSet.getLong("userid") : null,
                    log.getProjectId());
            projectOwner = owner != null && owner == currentUserid;
        }
        if (!self && !projectOwner) {
            throw new BusinessException("无权删除该工时记录");
        }
        workLogMapper.deleteById(logId);
    }

    public CapacitySummary capacity(long projectId, long currentUserid, LocalDate from, LocalDate to) {
        projectAccess.requireAccess(projectId, currentUserid);
        if (from == null || to == null) {
            throw new BusinessException("请选择日期区间");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 0) {
            throw new BusinessException("日期区间无效");
        }
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException("日期区间最多 " + MAX_RANGE_DAYS + " 天");
        }
        List<LogRow> rows = jdbcTemplate.query(
                "select userid, task_id, minutes from project_task_work_logs "
                        + "where project_id = ? and work_date between ? and ? and deleted_at is null",
                (resultSet, rowNum) -> new LogRow(
                        resultSet.getLong("userid"),
                        resultSet.getLong("task_id"),
                        resultSet.getInt("minutes")),
                projectId, from, to);
        CapacityCalculator.Result result = CapacityCalculator.summarize(from, to, rows);
        return new CapacitySummary(from, to, result.workdays(), CapacityCalculator.DAILY_MINUTES, result.members());
    }

    /** 导出项目工时 CSV。 */
    public String exportCsv(long projectId, long currentUserid) {
        projectAccess.requireAccess(projectId, currentUserid);
        List<WorkLog> logs = workLogMapper.selectList(new LambdaQueryWrapper<WorkLog>()
                .eq(WorkLog::getProjectId, projectId)
                .orderByAsc(WorkLog::getWorkDate)
                .orderByAsc(WorkLog::getId));
        StringBuilder csv = new StringBuilder();
        csv.append("id,task_id,userid,work_date,minutes,note\n");
        for (WorkLog log : logs) {
            csv.append(log.getId()).append(',')
                    .append(log.getTaskId()).append(',')
                    .append(log.getUserid()).append(',')
                    .append(log.getWorkDate()).append(',')
                    .append(log.getMinutes()).append(',')
                    .append(escapeCsv(log.getNote()))
                    .append('\n');
        }
        return csv.toString();
    }

    private static String escapeCsv(String value) {
        return Csv.escape(value);
    }

    private void requireTaskAccess(long taskId, long currentUserid) {
        Long projectId = jdbcTemplate.query(
                "select project_id from project_tasks where id = ? and deleted_at is null",
                rs -> rs.next() ? rs.getLong("project_id") : null,
                taskId);
        if (projectId == null || projectId == 0) {
            throw new BusinessException("任务不存在");
        }
        projectAccess.requireAccess(projectId, currentUserid);
    }

    private static String payloadHash(CreateWorklogRequest request) {
        String raw = request.minutes() + "|" + request.workDate() + "|" + (request.note() == null ? "" : request.note());
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            return null;
        }
    }

    private static WorklogView toView(WorkLog log) {
        return new WorklogView(
                log.getId(),
                log.getTaskId(),
                log.getUserid(),
                log.getMinutes() == null ? 0 : log.getMinutes(),
                log.getWorkDate(),
                log.getNote());
    }
}
