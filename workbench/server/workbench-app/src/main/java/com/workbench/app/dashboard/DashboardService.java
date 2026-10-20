package com.workbench.app.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.app.dashboard.DashboardDtos.DashboardView;
import com.workbench.app.dashboard.DashboardDtos.LayoutItem;
import com.workbench.app.dashboard.DashboardDtos.LayoutView;
import com.workbench.app.dashboard.DashboardDtos.WidgetDef;
import com.workbench.common.api.BusinessException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 首页板块仪表盘：板块目录 + 个人布局 + 各板块聚合数据。
 *
 * <p>聚合数据用 JdbcTemplate 直查，保持在单个接口内返回，避免首页发起十几个请求。</p>
 */
@Service
public class DashboardService {

    private static final List<WidgetDef> CATALOG = List.of(
            new WidgetDef("my-tasks", "我的任务", "工作", 6, 2),
            new WidgetDef("daily-report", "今日日报", "协作", 4, 1),
            new WidgetDef("capacity", "工时容量", "工作", 4, 1),
            new WidgetDef("notifications", "未读通知", "协作", 4, 1),
            new WidgetDef("activity", "最近动态", "协作", 6, 2),
            new WidgetDef("board", "看板缩略", "项目", 6, 2),
            new WidgetDef("gantt", "本周排期", "项目", 6, 2),
            new WidgetDef("summary", "项目概况", "项目", 6, 1),
            new WidgetDef("search", "全局搜索", "工具", 4, 1));

    private static final List<LayoutItem> DEFAULT_LAYOUT = List.of(
            new LayoutItem("my-tasks", 0, 0, 6, 2),
            new LayoutItem("daily-report", 6, 0, 3, 1),
            new LayoutItem("capacity", 9, 0, 3, 1),
            new LayoutItem("notifications", 6, 1, 3, 1),
            new LayoutItem("summary", 9, 1, 3, 1),
            new LayoutItem("activity", 0, 2, 6, 2),
            new LayoutItem("board", 6, 2, 6, 2),
            new LayoutItem("gantt", 0, 4, 6, 2),
            new LayoutItem("search", 6, 4, 6, 1));

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WidgetDef> catalog() {
        return CATALOG;
    }

    public DashboardView me(long userid) {
        List<LayoutItem> layout = loadLayout(userid);
        return new DashboardView(CATALOG, layout, data(userid), statusOf(userid), statusNoteOf(userid),
                isLayoutSaved(userid));
    }

    public LayoutView saveLayout(long userid, List<LayoutItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("布局不能为空");
        }
        Set<String> known = CATALOG.stream().map(WidgetDef::id).collect(java.util.stream.Collectors.toSet());
        for (LayoutItem item : items) {
            if (!known.contains(item.id()) && !item.id().startsWith("project:")) {
                throw new BusinessException("未知板块：" + item.id());
            }
            if (item.id().startsWith("project:")) {
                String rawId = item.id().substring("project:".length());
                try {
                    Long projectId = Long.parseLong(rawId);
                    Long visible = jdbc.queryForObject(
                            "select count(*) from projects p where p.id = ? and p.deleted_at is null and "
                                    + "(p.userid = ? or exists (select 1 from project_users pu "
                                    + "where pu.project_id = p.id and pu.userid = ?))",
                            Long.class, projectId, userid, userid);
                    if (visible == null || visible == 0) {
                        throw new BusinessException("无权添加该项目板块");
                    }
                } catch (NumberFormatException exception) {
                    throw new BusinessException("项目板块 id 非法");
                }
            }
            if (item.w() < 1 || item.w() > 12 || item.h() < 1 || item.h() > 6 || item.x() < 0 || item.y() < 0) {
                throw new BusinessException("板块尺寸超出范围");
            }
        }
        try {
            String json = objectMapper.writeValueAsString(items);
            jdbc.update("insert into user_dashboard_layouts (userid, layout_json, updated_at) values (?, ?, now()) "
                    + "on duplicate key update layout_json = values(layout_json), updated_at = now()", userid, json);
        } catch (Exception exception) {
            throw new BusinessException("布局保存失败");
        }
        return new LayoutView(items);
    }

    public LayoutView resetLayout(long userid) {
        try {
            jdbc.update("delete from user_dashboard_layouts where userid = ?", userid);
        } catch (RuntimeException exception) {
            // 没有布局时删除无影响
        }
        return new LayoutView(DEFAULT_LAYOUT);
    }

    private boolean isLayoutSaved(long userid) {
        try {
            Long total = jdbc.queryForObject(
                    "select count(*) from user_dashboard_layouts where userid = ?", Long.class, userid);
            return total != null && total > 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<LayoutItem> loadLayout(long userid) {
        try {
            List<String> rows = jdbc.queryForList(
                    "select layout_json from user_dashboard_layouts where userid = ?", String.class, userid);
            if (rows.isEmpty()) {
                return DEFAULT_LAYOUT;
            }
            return objectMapper.readValue(rows.get(0), new TypeReference<List<LayoutItem>>() {
            });
        } catch (Exception exception) {
            return DEFAULT_LAYOUT;
        }
    }

    private Map<String, Object> data(long userid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("my-tasks", jdbc.queryForList(
                "select id, name, project_id, end_at, p_level from project_tasks "
                        + "where owner_userid = ? and complete_at is null and deleted_at is null "
                        + "and project_id in (select id from projects where deleted_at is null) "
                        + "order by (end_at is null), end_at asc, id desc limit 8", userid));
        List<Map<String, Object>> projects = jdbc.queryForList(
                "select p.id, p.name, "
                        + "(select count(*) from project_users pu2 where pu2.project_id = p.id) as members, "
                        + "(select count(*) from project_tasks t where t.project_id = p.id and t.deleted_at is null) as total, "
                        + "(select count(*) from project_tasks t where t.project_id = p.id and t.deleted_at is null "
                        + "and t.complete_at is not null) as done "
                        + "from projects p where p.deleted_at is null and (p.userid = ? or exists ("
                        + "select 1 from project_users pu where pu.project_id = p.id and pu.userid = ?)) "
                        + "order by p.id desc", userid, userid);
        for (Map<String, Object> project : projects) {
            long total = ((Number) project.get("total")).longValue();
            long done = ((Number) project.get("done")).longValue();
            long percent = total == 0 ? 0 : Math.round(done * 100.0 / total);
            data.put("project:" + project.get("id"), Map.of(
                    "id", project.get("id"),
                    "name", project.get("name"),
                    "members", project.get("members"),
                    "total", total,
                    "done", done,
                    "percent", percent));
        }
        data.put("daily-report", jdbc.queryForList(
                "select id, status, submitted_at from daily_reports where userid = ? and report_date = curdate() "
                        + "and deleted_at is null limit 1", userid));
        long weekMinutes = count("select coalesce(sum(minutes),0) from project_task_work_logs "
                + "where userid = ? and deleted_at is null and work_date >= date_sub(curdate(), interval 7 day)", userid);
        int workdays = workdaysBetween(LocalDate.now().minusDays(6), LocalDate.now());
        data.put("capacity", Map.of(
                "minutes", weekMinutes,
                "available", workdays * 480L,
                "days", workdays));
        data.put("notifications", Map.of(
                "unread", count("select count(*) from notifications where userid = ? and read_at is null", userid),
                "latest", jdbc.queryForList(
                        "select id, title, created_at from notifications where userid = ? "
                                + "order by id desc limit 5", userid)));
        data.put("activity", jdbc.queryForList(
                "select a.id, a.action, a.detail, a.created_at, a.project_id from activity_logs a "
                        + "where a.project_id in (select p.id from projects p where p.userid = ? or exists ("
                        + "select 1 from project_users pu where pu.project_id = p.id and pu.userid = ?)) "
                        + "order by a.id desc limit 8", userid, userid));
        data.put("board", jdbc.queryForList(
                "select p.id as project_id, p.name, count(t.id) as open_tasks from projects p "
                        + "left join project_tasks t on t.project_id = p.id and t.complete_at is null "
                        + "and t.deleted_at is null where p.deleted_at is null and (p.userid = ? or exists ("
                        + "select 1 from project_users pu where pu.project_id = p.id and pu.userid = ?)) "
                        + "group by p.id, p.name order by open_tasks desc limit 5", userid, userid));
        data.put("gantt", jdbc.queryForList(
                "select t.id, t.name, t.start_at, t.end_at, t.project_id from project_tasks t "
                        + "where t.deleted_at is null and t.complete_at is null and t.start_at is not null "
                        + "and t.end_at is not null and t.end_at >= curdate() "
                        + "and t.project_id in (select p.id from projects p where p.deleted_at is null and (p.userid = ? or exists ("
                        + "select 1 from project_users pu where pu.project_id = p.id and pu.userid = ?))) "
                        + "order by t.start_at asc limit 8", userid, userid));
        Map<String, Object> summary = jdbc.queryForMap(
                "select "
                        + "(select count(*) from projects p where p.deleted_at is null and (p.userid = ? or exists ("
                        + "select 1 from project_users pu where pu.project_id = p.id and pu.userid = ?))) as projects, "
                        + "(select count(*) from project_tasks t join projects p on p.id = t.project_id "
                        + "where p.deleted_at is null and t.complete_at is null and t.deleted_at is null and (p.userid = ? "
                        + "or exists (select 1 from project_users pu where pu.project_id = p.id and pu.userid = ?))) as open_tasks, "
                        + "(select count(*) from project_tasks t join projects p on p.id = t.project_id "
                        + "where p.deleted_at is null and t.complete_at is null and t.deleted_at is null and t.end_at < now() "
                        + "and (p.userid = ? or exists (select 1 from project_users pu where pu.project_id = p.id "
                        + "and pu.userid = ?))) as overdue",
                userid, userid, userid, userid, userid, userid);
        data.put("summary", Map.of(
                "projects", summary.get("projects"),
                "openTasks", summary.get("open_tasks"),
                "overdue", summary.get("overdue")));
        return data;
    }

    private String statusOf(long userid) {
        try {
            List<String> rows = jdbc.queryForList("select status from user_status where userid = ?", String.class, userid);
            return rows.isEmpty() ? "idle" : rows.get(0);
        } catch (RuntimeException exception) {
            return "idle";
        }
    }

    private String statusNoteOf(long userid) {
        try {
            List<String> rows = jdbc.queryForList("select note from user_status where userid = ?", String.class, userid);
            return rows.isEmpty() ? "" : rows.get(0);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static int workdaysBetween(LocalDate from, LocalDate to) {
        int days = 0;
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            DayOfWeek day = cursor.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return Math.max(days, 1);
    }
}
