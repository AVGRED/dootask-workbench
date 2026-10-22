package com.workbench.app.admin;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端总览：全部项目、全员日报（仅管理员）。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOverviewController {

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;

    public AdminOverviewController(JdbcTemplate jdbc, SystemAdmin systemAdmin) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
    }

    @GetMapping("/projects")
    public ApiResponse<List<Map<String, Object>>> projects(Principal principal) {
        requireAdmin(principal);
        return ApiResponse.ok(jdbc.queryForList(
                "select p.id, p.name, p.userid as owner_userid, "
                        + "(select u.nickname from users u where u.userid = p.userid) as owner_name, "
                        + "(select count(*) from project_users pu where pu.project_id = p.id) as members, "
                        + "(select count(*) from project_tasks t where t.project_id = p.id and t.deleted_at is null) as total, "
                        + "(select count(*) from project_tasks t where t.project_id = p.id and t.deleted_at is null "
                        + "and t.complete_at is not null) as done, "
                        + "(select count(*) from project_tasks t where t.project_id = p.id and t.deleted_at is null "
                        + "and t.complete_at is null and t.end_at < now()) as overdue, "
                        + "p.updated_at "
                        + "from projects p where p.deleted_at is null order by p.id desc"));
    }

    @GetMapping("/reports")
    public ApiResponse<List<Map<String, Object>>> reports(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireAdmin(principal);
        LocalDate day = date == null ? LocalDate.now() : date;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select d.id, d.userid, u.nickname, u.email, d.report_date, d.content_done, "
                        + "d.content_blocker, d.content_plan, d.minutes, d.status, d.submitted_at "
                        + "from daily_reports d join users u on u.userid = d.userid "
                        + "where d.deleted_at is null and u.deleted_at is null and d.report_date = ? "
                        + "order by d.userid", day);
        return ApiResponse.ok(rows);
    }

    @GetMapping("/reports/missing")
    public ApiResponse<List<Map<String, Object>>> missing(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireAdmin(principal);
        LocalDate day = date == null ? LocalDate.now() : date;
        return ApiResponse.ok(jdbc.queryForList(
                "select u.userid, u.nickname, u.email from users u where u.deleted_at is null "
                        + "and not exists (select 1 from daily_reports d where d.userid = u.userid "
                        + "and d.report_date = ? and d.deleted_at is null) order by u.userid", day));
    }

    private void requireAdmin(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        systemAdmin.requireAdmin(Long.parseLong(principal.getName()));
    }
}
