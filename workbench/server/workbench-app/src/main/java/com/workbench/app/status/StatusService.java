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

    public List<Map<String, Object>> team(long currentUserid) {
        systemAdmin.requireAdmin(currentUserid);
        String sql = "select u.userid, u.nickname, u.email, coalesce(u.avatar, '') as avatar, "
                + "coalesce(s.status, 'idle') as status, "
                + "coalesce(s.note, '') as note, s.updated_at as status_at, "
                + "(select t.name from project_tasks t where t.owner_userid = u.userid and t.complete_at is null "
                + "and t.deleted_at is null order by t.id desc limit 1) as current_task, "
                + "(select count(*) from daily_reports d where d.userid = u.userid and d.report_date = curdate() "
                + "and d.deleted_at is null) as report_today, "
                + "(select coalesce(sum(w.minutes),0) from project_task_work_logs w where w.userid = u.userid "
                + "and w.deleted_at is null and w.work_date >= date_sub(curdate(), interval 7 day)) as week_minutes "
                + "from users u left join user_status s on s.userid = u.userid "
                + "where u.deleted_at is null ";
        return jdbc.queryForList(sql + "order by s.updated_at is null, s.updated_at desc, u.userid");
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
