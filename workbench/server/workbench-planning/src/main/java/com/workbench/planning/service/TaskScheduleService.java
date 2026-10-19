package com.workbench.planning.service;

import com.workbench.planning.schedule.ScheduleCalculator;
import com.workbench.planning.schedule.ScheduleCalculator.Edge;
import com.workbench.planning.schedule.ScheduleCalculator.TaskSlot;
import com.workbench.planning.web.ScheduleDtos.ChangeView;
import com.workbench.planning.web.ScheduleDtos.PreviewRequest;
import com.workbench.planning.web.ScheduleDtos.PreviewResponse;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 自动排期：预演（只读，不写库）。
 *
 * <p>数据从 project_tasks / project_task_dependencies 读取，算法在 {@link ScheduleCalculator} 里，
 * 便于单独测试。</p>
 */
@Service
public class TaskScheduleService {

    private final JdbcTemplate jdbcTemplate;

    public TaskScheduleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PreviewResponse preview(long projectId, PreviewRequest request) {
        List<TaskSlot> slots = jdbcTemplate.query(
                "select id, start_at, end_at from project_tasks where project_id = ? and deleted_at is null",
                (resultSet, rowNum) -> new TaskSlot(
                        resultSet.getLong("id"),
                        resultSet.getTimestamp("start_at") == null ? null : resultSet.getTimestamp("start_at").toLocalDateTime(),
                        resultSet.getTimestamp("end_at") == null ? null : resultSet.getTimestamp("end_at").toLocalDateTime()),
                projectId);

        List<Edge> edges = jdbcTemplate.query(
                "select predecessor_task_id, successor_task_id from project_task_dependencies where project_id = ?",
                (resultSet, rowNum) -> new Edge(
                        resultSet.getLong("predecessor_task_id"),
                        resultSet.getLong("successor_task_id")),
                projectId);

        List<ScheduleCalculator.Change> changes = ScheduleCalculator.preview(
                slots,
                edges,
                request.anchorTaskId(),
                request.anchorStart(),
                request.anchorEnd(),
                toWorkdays(request.workdays()));

        List<ChangeView> views = new ArrayList<>();
        for (ScheduleCalculator.Change change : changes) {
            views.add(new ChangeView(change.taskId(), change.oldStart(), change.oldEnd(),
                    change.newStart(), change.newEnd()));
        }
        return new PreviewResponse(views, views.size());
    }

    private Set<DayOfWeek> toWorkdays(List<Integer> workdays) {
        if (workdays == null || workdays.isEmpty()) {
            return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        Set<DayOfWeek> result = new HashSet<>();
        for (Integer day : workdays) {
            if (day != null && day >= 1 && day <= 7) {
                result.add(DayOfWeek.of(day));
            }
        }
        return result;
    }

    /** 供测试使用：直接算一次预演（不查库）。 */
    public static List<ChangeView> compute(
            List<TaskSlot> slots,
            List<Edge> edges,
            long anchorId,
            LocalDateTime start,
            LocalDateTime end,
            Set<DayOfWeek> workdays) {
        return ScheduleCalculator.preview(slots, edges, anchorId, start, end, workdays).stream()
                .map(change -> new ChangeView(change.taskId(), change.oldStart(), change.oldEnd(),
                        change.newStart(), change.newEnd()))
                .toList();
    }
}
