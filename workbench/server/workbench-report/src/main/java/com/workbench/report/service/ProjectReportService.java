package com.workbench.report.service;

import com.workbench.project.service.ProjectAccess;
import com.workbench.report.web.ReportDtos.DayPoint;
import com.workbench.report.web.ReportDtos.MemberStat;
import com.workbench.report.web.ReportDtos.ProjectReport;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 项目报表：任务完成情况、工时汇总、里程碑进度。 */
@Service
public class ProjectReportService {

    private static final int MAX_DAYS = 93;

    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccess projectAccess;

    public ProjectReportService(JdbcTemplate jdbcTemplate, ProjectAccess projectAccess) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectAccess = projectAccess;
    }

    public ProjectReport report(long projectId, long currentUserid, LocalDate from, LocalDate to) {
        projectAccess.requireAccess(projectId, currentUserid);
        LocalDate safeTo = to == null ? LocalDate.now() : to;
        LocalDate safeFrom = from == null ? safeTo.minusDays(29) : from;
        if (ChronoUnit.DAYS.between(safeFrom, safeTo) > MAX_DAYS) {
            safeFrom = safeTo.minusDays(MAX_DAYS);
        }

        long total = count("select count(*) from project_tasks where project_id = ? and deleted_at is null", projectId);
        long completed = count("select count(*) from project_tasks where project_id = ? and deleted_at is null "
                + "and complete_at is not null", projectId);
        long overdue = count("select count(*) from project_tasks where project_id = ? and deleted_at is null "
                + "and complete_at is null and end_at is not null and end_at < now()", projectId);
        long percent = total == 0 ? 0 : Math.round(completed * 100.0 / total);

        long minutes = count("select coalesce(sum(minutes), 0) from project_task_work_logs "
                + "where project_id = ? and deleted_at is null and work_date between ? and ?", projectId, safeFrom, safeTo);

        long milestoneTotal = count("select count(*) from project_milestones where project_id = ?", projectId);
        long milestoneDone = count("select count(*) from project_milestones where project_id = ? "
                + "and status = 'completed'", projectId);

        List<MemberStat> members = jdbcTemplate.query(
                "select w.userid, u.email, coalesce(sum(w.minutes), 0) as minutes, "
                        + "count(distinct w.task_id) as tasks "
                        + "from project_task_work_logs w left join users u on u.userid = w.userid "
                        + "where w.project_id = ? and w.deleted_at is null and w.work_date between ? and ? "
                        + "group by w.userid, u.email order by minutes desc",
                (rs, rowNum) -> new MemberStat(rs.getLong("userid"), rs.getString("email"),
                        rs.getLong("minutes"), rs.getLong("tasks")),
                projectId, safeFrom, safeTo);

        java.util.Map<java.time.LocalDate, Long> minutesByDay = new java.util.HashMap<>();
        jdbcTemplate.query("select work_date, coalesce(sum(minutes), 0) as minutes from project_task_work_logs "
                        + "where project_id = ? and deleted_at is null and work_date between ? and ? group by work_date",
                rs -> {
                    minutesByDay.put(rs.getDate("work_date").toLocalDate(), rs.getLong("minutes"));
                }, projectId, safeFrom, safeTo);

        java.util.Map<java.time.LocalDate, Long> completedByDay = new java.util.HashMap<>();
        jdbcTemplate.query("select date(complete_at) as day, count(*) as total from project_tasks "
                        + "where project_id = ? and deleted_at is null and complete_at is not null "
                        + "and date(complete_at) between ? and ? group by date(complete_at)",
                rs -> {
                    completedByDay.put(rs.getDate("day").toLocalDate(), rs.getLong("total"));
                }, projectId, safeFrom, safeTo);

        List<DayPoint> days = new ArrayList<>();
        for (LocalDate cursor = safeFrom; !cursor.isAfter(safeTo); cursor = cursor.plusDays(1)) {
            days.add(new DayPoint(cursor, minutesByDay.getOrDefault(cursor, 0L),
                    completedByDay.getOrDefault(cursor, 0L)));
        }

        return new ProjectReport(projectId, safeFrom, safeTo, total, completed, overdue, percent,
                minutes, milestoneTotal, milestoneDone, members, days);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
