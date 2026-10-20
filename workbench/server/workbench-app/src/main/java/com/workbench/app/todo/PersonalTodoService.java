package com.workbench.app.todo;

import com.workbench.common.api.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 个人待办：不属于任何项目，每人每天自己记录的事。
 *
 * <p>只操作自己的数据，别人的 id 一律按"不存在"处理。</p>
 */
@Service
public class PersonalTodoService {

    private final JdbcTemplate jdbc;

    public PersonalTodoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(long userid) {
        return jdbc.queryForList(
                "select id, title, done, todo_date, created_at from personal_todos "
                        + "where userid = ? and deleted_at is null "
                        + "order by done asc, sort asc, id desc",
                userid);
    }

    public Map<String, Object> create(long userid, String title, LocalDate todoDate) {
        String safe = validate(title);
        jdbc.update(
                "insert into personal_todos (userid, title, done, todo_date, sort, created_at, updated_at) "
                        + "values (?, ?, 0, ?, 0, now(), now())",
                userid, safe, todoDate);
        return latest(userid);
    }

    public Map<String, Object> update(long userid, long id, String title, Boolean done) {
        Map<String, Object> existing = requireOwned(userid, id);
        String safeTitle = title == null ? (String) existing.get("title") : validate(title);
        int doneValue = done == null
                ? ((Number) existing.get("done")).intValue()
                : (done ? 1 : 0);
        jdbc.update(
                "update personal_todos set title = ?, done = ?, updated_at = now() "
                        + "where id = ? and userid = ? and deleted_at is null",
                safeTitle, doneValue, id, userid);
        return byId(userid, id);
    }

    public void delete(long userid, long id) {
        requireOwned(userid, id);
        jdbc.update("update personal_todos set deleted_at = now() where id = ? and userid = ?", id, userid);
    }

    private Map<String, Object> latest(long userid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, title, done, todo_date, created_at from personal_todos "
                        + "where userid = ? and deleted_at is null order by id desc limit 1",
                userid);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> byId(long userid, long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, title, done, todo_date, created_at from personal_todos "
                        + "where id = ? and userid = ? and deleted_at is null",
                id, userid);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "待办不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireOwned(long userid, long id) {
        return byId(userid, id);
    }

    private String validate(String title) {
        String safe = title == null ? "" : title.trim();
        if (safe.isEmpty()) {
            throw new BusinessException("待办内容不能为空");
        }
        if (safe.length() > 200) {
            throw new BusinessException("待办内容最多 200 字");
        }
        return safe;
    }
}
