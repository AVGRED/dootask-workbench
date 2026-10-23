package com.workbench.app.status;

import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 员工状态：手动状态（专注/空闲/会议/外出/请假）+ 自动上下文（进行中任务、今日日报、近 7 天工时）。
 *
 * <p>管理员看全公司；非管理员只看自己所在部门。</p>
 */
@Service
public class StatusService {

    public static final Set<String> ALLOWED = Set.of("focus", "idle", "meeting", "away", "leave");

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;

    public StatusService(JdbcTemplate jdbc, SystemAdmin systemAdmin) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
    }

    public Map<String, Object> myStatus(long userid) {
        return row(userid);
    }

    public Map<String, Object> setStatus(long userid, String status, String note) {
        String safe = status == null ? "idle" : status;
        if (!ALLOWED.contains(safe)) {
            throw new BusinessException("状态只能是 focus/idle/meeting/away/leave");
        }
        jdbc.update("insert into user_status (userid, status, note, updated_at) values (?, ?, ?, now()) "
                        + "on duplicate key update status = values(status), note = values(note), updated_at = now()",
                userid, safe, note == null ? "" : note.trim());
        return row(userid);
    }

    /** 超级管理员代改员工状态（例如员工忘切状态）。 */
    public Map<String, Object> setStatusFor(long operatorUserid, long targetUserid, String status, String note) {
        systemAdmin.requireSuper(operatorUserid);
        Long exists = jdbc.queryForObject(
                "select count(*) from users where userid = ? and deleted_at is null", Long.class, targetUserid);
        if (exists == null || exists == 0) {
            throw new BusinessException(404, "用户不存在");
        }
        String safe = status == null ? "idle" : status;
        if (!ALLOWED.contains(safe)) {
            throw new BusinessException("状态只能是 focus/idle/meeting/away/leave");
        }
        jdbc.update("insert into user_status (userid, status, note, updated_at) values (?, ?, ?, now()) "
                        + "on duplicate key update status = values(status), note = values(note), updated_at = now()",
                targetUserid, safe, note == null ? "" : note.trim());
        return row(targetUserid);
    }

    public List<Map<String, Object>> team(long currentUserid) {
        systemAdmin.requireAdmin(currentUserid);
        return jdbc.queryForList(
                "select u.userid, u.nickname, u.email, coalesce(u.avatar, '') as avatar, "
                        + "coalesce(s.status, 'idle') as status, coalesce(s.note, '') as note, "
                        + "s.updated_at as status_at, t.current_task, "
                        + "coalesce(r.report_today, 0) as report_today, coalesce(w.week_minutes, 0) as week_minutes, "
                        + "coalesce(u.role, 'staff') as role, coalesce(u.super_admin, 0) as superAdmin "
                        + "from users u "
                        + "left join user_status s on s.userid = u.userid "
                        + "left join (select t1.owner_userid, t1.name as current_task from project_tasks t1 "
                        + "join (select owner_userid, max(id) as id from project_tasks where complete_at is null "
                        + "and deleted_at is null group by owner_userid) latest on latest.id = t1.id) t "
                        + "on t.owner_userid = u.userid "
                        + "left join (select userid, count(*) as report_today from daily_reports "
                        + "where report_date = curdate() and deleted_at is null group by userid) r on r.userid = u.userid "
                        + "left join (select userid, sum(minutes) as week_minutes from project_task_work_logs "
                        + "where deleted_at is null and work_date >= date_sub(curdate(), interval 7 day) "
                        + "group by userid) w on w.userid = u.userid "
                        // 状态墙只展示员工，管理员账号不上墙
                        + "where u.deleted_at is null "
                        + "order by s.updated_at is null, s.updated_at desc, u.userid");
    }

    private Map<String, Object> row(long userid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select coalesce(status,'idle') as status, coalesce(note,'') as note, updated_at "
                        + "from user_status where userid = ?", userid);
        if (rows.isEmpty()) {
            return Map.of("status", "idle", "note", "");
        }
        return rows.get(0);
    }
}
