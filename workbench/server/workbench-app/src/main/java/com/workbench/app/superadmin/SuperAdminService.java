package com.workbench.app.superadmin;

import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 超级管理员专属操作：代写日报、代建待办、强制下线、删除他人任务/项目，并记录审计。
 *
 * <p>所有入口都会先 requireSuper，普通管理员调用返回 403。</p>
 */
@Service
public class SuperAdminService {

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public SuperAdminService(JdbcTemplate jdbc, SystemAdmin systemAdmin,
                             org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> writeReport(long operator, long userid, LocalDate date,
                                           String done, String blocker, String plan, Integer minutes) {
        systemAdmin.requireSuper(operator);
        requireUser(userid);
        LocalDate day = date == null ? LocalDate.now() : date;
        if (done == null || done.isBlank()) {
            throw new BusinessException("请填写今日完成内容");
        }

        Long exists = jdbc.queryForObject(
                "select count(*) from daily_reports where userid = ? and report_date = ? and deleted_at is null",
                Long.class, userid, day);

        if (exists != null && exists > 0) {
            jdbc.update("update daily_reports set content_done = ?, content_blocker = ?, content_plan = ?, "
                            + "minutes = ?, status = 1, submitted_at = now(), updated_at = now() "
                            + "where userid = ? and report_date = ? and deleted_at is null",
                    done, blocker == null ? "" : blocker, plan == null ? "" : plan,
                    minutes == null ? 0 : minutes, userid, day);
        } else {
            jdbc.update("insert into daily_reports (userid, report_date, content_done, content_blocker, content_plan, "
                            + "minutes, status, submitted_at, created_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?, 1, now(), now(), now())",
                    userid, day, done, blocker == null ? "" : blocker, plan == null ? "" : plan,
                    minutes == null ? 0 : minutes);
        }

        audit(operator, "report.write", userid, day.toString());
        return Map.of("userid", userid, "reportDate", day.toString());
    }

    public Map<String, Object> addTodo(long operator, long userid, String title) {
        systemAdmin.requireSuper(operator);
        requireUser(userid);
        String safe = title == null ? "" : title.trim();
        if (safe.isEmpty()) {
            throw new BusinessException("待办内容不能为空");
        }
        jdbc.update("insert into personal_todos (userid, title, done, sort, created_at, updated_at) "
                + "values (?, ?, 0, 0, now(), now())", userid, safe);
        audit(operator, "todo.create", userid, safe);
        return Map.of("userid", userid, "title", safe);
    }

    /** 重置任意用户密码（含管理员），并让对方的登录态失效。 */
    public Map<String, Object> resetPassword(long operator, long userid, String password) {
        systemAdmin.requireSuper(operator);
        requireUser(userid);
        if (password == null || password.length() < 8) {
            throw new BusinessException("密码至少 8 位");
        }
        String encoded = passwordEncoder.encode(password);
        jdbc.update("update users set password = ?, encrypt = '', token_version = coalesce(token_version, 0) + 1, "
                + "updated_at = now() where userid = ? and deleted_at is null", encoded, userid);
        audit(operator, "user.reset_password", userid, "");
        return Map.of("userid", userid, "ok", true);
    }

    public Map<String, Object> forceLogout(long operator, long userid) {
        systemAdmin.requireSuper(operator);
        requireUser(userid);
        jdbc.update("update users set token_version = coalesce(token_version, 0) + 1, updated_at = now() "
                + "where userid = ? and deleted_at is null", userid);
        audit(operator, "user.force_logout", userid, "");
        return Map.of("userid", userid, "ok", true);
    }

    public Map<String, Object> deleteTask(long operator, long taskId) {
        systemAdmin.requireSuper(operator);
        int affected = jdbc.update("update project_tasks set deleted_at = now() where id = ? and deleted_at is null", taskId);
        if (affected == 0) {
            throw new BusinessException(404, "任务不存在或已删除");
        }
        audit(operator, "task.delete", null, "taskId=" + taskId);
        return Map.of("taskId", taskId, "ok", true);
    }

    public Map<String, Object> deleteProject(long operator, long projectId) {
        systemAdmin.requireSuper(operator);
        int affected = jdbc.update("update projects set deleted_at = now() where id = ? and deleted_at is null", projectId);
        if (affected == 0) {
            throw new BusinessException(404, "项目不存在或已删除");
        }
        audit(operator, "project.delete", null, "projectId=" + projectId);
        return Map.of("projectId", projectId, "ok", true);
    }

    public List<Map<String, Object>> auditLogs(long operator, int limit) {
        systemAdmin.requireSuper(operator);
        int safe = Math.min(Math.max(limit, 1), 200);
        return jdbc.queryForList(
                "select l.id, l.action, l.target_userid, l.detail, l.created_at, "
                        + "coalesce(o.nickname, '') as operator_name, coalesce(t.nickname, '') as target_name "
                        + "from super_audit_logs l "
                        + "left join users o on o.userid = l.operator_userid "
                        + "left join users t on t.userid = l.target_userid "
                        + "order by l.id desc limit ?", safe);
    }

    private void audit(long operator, String action, Long targetUserid, String detail) {
        jdbc.update("insert into super_audit_logs (operator_userid, action, target_userid, detail, created_at) "
                + "values (?, ?, ?, ?, now())", operator, action, targetUserid, detail == null ? "" : detail);
    }

    private void requireUser(long userid) {
        Long count = jdbc.queryForObject(
                "select count(*) from users where userid = ? and deleted_at is null", Long.class, userid);
        if (count == null || count == 0) {
            throw new BusinessException(404, "用户不存在");
        }
    }
}
