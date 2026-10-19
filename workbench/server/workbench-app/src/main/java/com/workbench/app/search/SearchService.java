package com.workbench.app.search;

import com.workbench.app.search.SearchResult.Item;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 全局搜索：只搜当前用户有权限看到的内容。
 *
 * <ul>
 *   <li>项目：自己负责的，或自己是成员的项目；</li>
 *   <li>任务：上面这些项目里未删除的任务；</li>
 *   <li>用户：按邮箱/昵称匹配（用于找同事加成员）。</li>
 * </ul>
 */
@Service
public class SearchService {

    private static final int GROUP_LIMIT = 5;

    private final JdbcTemplate jdbcTemplate;

    public SearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SearchResult search(long currentUserid, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return SearchResult.empty();
        }
        String like = "%" + keyword.trim() + "%";

        List<Item> projects = jdbcTemplate.query(
                "select p.id, p.name from projects p "
                        + "where p.deleted_at is null and p.name like ? "
                        + "and (p.userid = ? or exists (select 1 from project_users pu "
                        + "where pu.project_id = p.id and pu.userid = ?)) "
                        + "order by p.id desc limit " + GROUP_LIMIT,
                (rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("name"), "项目", "project"),
                like, currentUserid, currentUserid);

        List<Item> tasks = jdbcTemplate.query(
                "select t.id, t.name, t.project_id from project_tasks t "
                        + "where t.deleted_at is null and t.name like ? "
                        + "and exists (select 1 from projects p where p.id = t.project_id and p.deleted_at is null "
                        + "and (p.userid = ? or exists (select 1 from project_users pu2 "
                        + "where pu2.project_id = p.id and pu2.userid = ?))) "
                        + "order by t.id desc limit " + GROUP_LIMIT,
                (rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("name"),
                        "项目 #" + rs.getLong("project_id"), "task"),
                like, currentUserid, currentUserid);

        List<Item> users = jdbcTemplate.query(
                "select userid, email, nickname from users "
                        + "where deleted_at is null and (email like ? or nickname like ?) "
                        + "order by userid desc limit " + GROUP_LIMIT,
                (rs, rowNum) -> new Item(rs.getLong("userid"), rs.getString("nickname"),
                        rs.getString("email"), "user"),
                like, like);

        return new SearchResult(projects, tasks, users);
    }
}
