package com.workbench.planning.service;

import com.workbench.planning.web.GanttDtos.GanttEdge;
import com.workbench.planning.web.GanttDtos.GanttTask;
import com.workbench.planning.web.GanttDtos.GanttView;
import com.workbench.project.service.ProjectAccess;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 甘特图：只读，返回任务时间条与依赖线。
 *
 * <p>没有日期的任务不进入时间轴；时间范围取所有任务的最早开始与最晚结束，
 * 再向前后各放宽一天，避免边界贴边。</p>
 */
@Service
public class GanttService {

    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccess projectAccess;

    public GanttService(JdbcTemplate jdbcTemplate, ProjectAccess projectAccess) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectAccess = projectAccess;
    }

    public GanttView view(long projectId, long currentUserid) {
        projectAccess.requireAccess(projectId, currentUserid);

        List<GanttTask> tasks = jdbcTemplate.query(
                "select id, name, start_at, end_at, owner_userid, p_level, complete_at, milestone_id "
                        + "from project_tasks where project_id = ? and deleted_at is null "
                        + "and start_at is not null and end_at is not null order by start_at, id",
                (rs, rowNum) -> new GanttTask(
                        rs.getLong("id"),
                        rs.getString("name"),
                        toDate(rs.getTimestamp("start_at")),
                        toDate(rs.getTimestamp("end_at")),
                        rs.getLong("owner_userid"),
                        rs.getInt("p_level"),
                        rs.getTimestamp("complete_at") != null,
                        rs.getObject("milestone_id") == null ? null : rs.getLong("milestone_id")),
                projectId);

        List<GanttEdge> edges = jdbcTemplate.query(
                "select predecessor_task_id, successor_task_id from project_task_dependencies where project_id = ?",
                (rs, rowNum) -> new GanttEdge(
                        rs.getLong("predecessor_task_id"),
                        rs.getLong("successor_task_id")),
                projectId);

        if (tasks.isEmpty()) {
            LocalDate today = LocalDate.now();
            return new GanttView(projectId, today.minusDays(1), today.plusDays(7), List.of(), edges);
        }
        LocalDate start = tasks.stream().map(GanttTask::startDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate end = tasks.stream().map(GanttTask::endDate).max(LocalDate::compareTo).orElse(LocalDate.now());
        return new GanttView(projectId, start.minusDays(1), end.plusDays(1), tasks, edges);
    }

    private static LocalDate toDate(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toLocalDate();
    }
}
