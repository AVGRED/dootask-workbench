package com.workbench.app.starmap;

import com.workbench.user.service.SystemAdmin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 星图：把员工画成节点、把「一起做过项目」画成连线。
 *
 * <p>管理员看全公司；普通员工只看自己 + 与自己有共同项目的人（含这些人在这些项目内的互相连边）。
 * 项目归属同时来自 projects.userid（创建者）与 project_users（成员表），两者都要算进去。</p>
 */
@Service
public class StarMapService {

    private static final int MAX_NODES = 500;
    /** 单项目参与配对的成员上限，避免大项目 O(n²) 爆炸。 */
    private static final int MAX_MEMBERS_PER_PROJECT = 60;
    /** 返回给前端的边数上限。 */
    private static final int MAX_EDGES = 2000;

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;

    public StarMapService(JdbcTemplate jdbc, SystemAdmin systemAdmin) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
    }

    public Map<String, Object> map(long currentUserid) {
        boolean admin = systemAdmin.isAdmin(currentUserid);

        List<Map<String, Object>> projects = admin
                ? jdbc.queryForList("select id, userid, name from projects where deleted_at is null")
                : jdbc.queryForList(
                        "select id, userid, name from projects where deleted_at is null "
                                + "and (userid = ? or id in (select project_id from project_users where userid = ?))",
                        currentUserid, currentUserid);

        Set<Long> projectIds = new LinkedHashSet<>();
        Map<Long, String> projectNames = new LinkedHashMap<>();
        for (Map<String, Object> row : projects) {
            long id = ((Number) row.get("id")).longValue();
            projectIds.add(id);
            Object name = row.get("name");
            projectNames.put(id, name == null ? "" : name.toString());
        }

        // project -> userids（创建者 + 成员）
        Map<Long, Set<Long>> projectUsers = new LinkedHashMap<>();
        for (Map<String, Object> row : projects) {
            long projectId = ((Number) row.get("id")).longValue();
            long owner = ((Number) row.get("userid")).longValue();
            projectUsers.computeIfAbsent(projectId, key -> new LinkedHashSet<>()).add(owner);
        }
        if (!projectIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(projectIds.size(), "?"));
            for (Map<String, Object> row : jdbc.queryForList(
                    "select project_id, userid from project_users where project_id in (" + placeholders + ")",
                    projectIds.toArray())) {
                long projectId = ((Number) row.get("project_id")).longValue();
                long userid = ((Number) row.get("userid")).longValue();
                projectUsers.computeIfAbsent(projectId, key -> new LinkedHashSet<>()).add(userid);
            }
        }

        Map<Long, Set<Long>> userProjects = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : projectUsers.entrySet()) {
            for (Long userid : entry.getValue()) {
                userProjects.computeIfAbsent(userid, key -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }

        Set<Long> visible = new LinkedHashSet<>();
        if (admin) {
            visible.addAll(userProjects.keySet());
        } else {
            visible.add(currentUserid);
            // userProjects 存的是「项目 id」，这里要展开成「同一个项目里的人」
            for (Long projectId : userProjects.getOrDefault(currentUserid, Set.of())) {
                visible.addAll(projectUsers.getOrDefault(projectId, Set.of()));
            }
        }
        if (admin) {
            // 管理员看全公司：没有项目的员工也应作为孤星出现
            for (Map<String, Object> row : jdbc.queryForList(
                    "select userid from users where deleted_at is null")) {
                visible.add(((Number) row.get("userid")).longValue());
            }
        }
        if (visible.size() > MAX_NODES) {
            visible = new LinkedHashSet<>(new ArrayList<>(visible).subList(0, MAX_NODES));
        }

        Map<Long, Map<String, Object>> profiles = new HashMap<>();
        if (!visible.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(visible.size(), "?"));
            for (Map<String, Object> row : jdbc.queryForList(
                    "select u.userid, u.nickname, u.email, coalesce(u.role, 'staff') as role, "
                            + "coalesce(u.avatar, '') as avatar, coalesce(s.status, 'idle') as status, "
                            + "coalesce(s.note, '') as note "
                            + "from users u left join user_status s on s.userid = u.userid "
                            + "where u.deleted_at is null and u.userid in (" + placeholders + ")",
                    visible.toArray())) {
                profiles.put(((Number) row.get("userid")).longValue(), row);
            }
        }

        // 连边：同一项目内两两相连；权重 = 共同项目数
        Map<String, Integer> weights = new LinkedHashMap<>();
        Map<String, java.util.List<String>> edgeProjects = new LinkedHashMap<>();
        boolean truncated = false;
        outer:
        for (Map.Entry<Long, Set<Long>> projectEntry : projectUsers.entrySet()) {
            long projectId = projectEntry.getKey();
            Set<Long> users = projectEntry.getValue();
            List<Long> list = new ArrayList<>(users);
            if (list.size() > MAX_MEMBERS_PER_PROJECT) {
                list = new ArrayList<>(list.subList(0, MAX_MEMBERS_PER_PROJECT));
                truncated = true;
            }
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    long a = list.get(i);
                    long b = list.get(j);
                    if (!visible.contains(a) || !visible.contains(b)) {
                        continue;
                    }
                    String key = a + ":" + b;
                    if (!weights.containsKey(key) && weights.size() >= MAX_EDGES) {
                        truncated = true;
                        break outer;
                    }
                    weights.merge(key, 1, Integer::sum);
                    String projectName = projectNames.getOrDefault(projectId, "");
                    if (!projectName.isBlank()) {
                        edgeProjects.computeIfAbsent(key, ignored -> new ArrayList<>()).add(projectName);
                    }
                }
            }
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Long userid : visible) {
            Map<String, Object> profile = profiles.get(userid);
            if (profile == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>(profile);
            node.put("projectCount", userProjects.getOrDefault(userid, Set.of()).size());
            node.put("self", userid == currentUserid);
            nodes.add(node);
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            String[] parts = entry.getKey().split(":");
            List<String> names = edgeProjects.getOrDefault(entry.getKey(), List.of());
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("from", Long.parseLong(parts[0]));
            edge.put("to", Long.parseLong(parts[1]));
            edge.put("weight", entry.getValue());
            edge.put("projects", names.size() > 5 ? names.subList(0, 5) : names);
            edges.add(edge);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", admin ? "all" : "collaborators");
        result.put("centerUserid", currentUserid);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("truncated", truncated);
        return result;
    }
}
