package com.workbench.app.meeting;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会议：先支持「粘贴腾讯会议链接 + 通知参会人」，
 * 以后接入腾讯会议 API 时只需把 joinUrl 的来源换掉。
 */
@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;

    public MeetingController(JdbcTemplate jdbc, SystemAdmin systemAdmin) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
    }

    public record SaveMeetingRequest(
            @NotBlank(message = "请填写会议主题") @Size(max = 120) String title,
            Long projectId,
            LocalDateTime startAt,
            Integer durationMinutes,
            @NotBlank(message = "请填写会议链接") @Size(max = 600) String joinUrl,
            @Size(max = 40) String meetingCode,
            @Size(max = 500) String description
    ) {
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(Principal principal,
                                                       @RequestParam(required = false) Long projectId,
                                                       @RequestParam(required = false) Integer limit) {
        long userid = currentUserid(principal);
        int size = limit == null || limit <= 0 || limit > 100 ? 50 : limit;
        StringBuilder sql = new StringBuilder(
                "select m.id, m.title, m.project_id, m.owner_userid, m.start_at, m.duration_minutes, "
                        + "m.join_url, m.meeting_code, m.platform, m.description, m.created_at, "
                        + "coalesce(u.nickname, '') as owner_name, coalesce(p.name, '') as project_name "
                        + "from meetings m left join users u on u.userid = m.owner_userid "
                        + "left join projects p on p.id = m.project_id "
                        + "where m.deleted_at is null ");
        List<Object> args = new java.util.ArrayList<>();
        if (projectId != null) {
            requireProjectVisible(userid, projectId);
            sql.append("and m.project_id = ? ");
            args.add(projectId);
        } else if (!systemAdmin.isAdmin(userid)) {
            sql.append("and (m.project_id is null or m.project_id in "
                    + "(select project_id from project_users where userid = ?)) ");
            args.add(userid);
        }
        sql.append("order by m.start_at desc, m.id desc limit ?");
        args.add(size);
        return ApiResponse.ok(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(Principal principal, @Valid @RequestBody SaveMeetingRequest request) {
        long userid = currentUserid(principal);
        LocalDateTime startAt = request.startAt() == null ? LocalDateTime.now() : request.startAt();
        int duration = request.durationMinutes() == null || request.durationMinutes() <= 0 ? 60 : request.durationMinutes();
        String code = request.meetingCode() == null ? "" : request.meetingCode().trim();
        String desc = request.description() == null ? "" : request.description().trim();

        if (request.projectId() == null) {
            // 全公司会议只有管理员能建
            systemAdmin.requireAdmin(userid);
        } else {
            requireProjectWrite(userid, request.projectId());
        }

        jdbc.update(
                "insert into meetings (title, project_id, owner_userid, start_at, duration_minutes, join_url, "
                        + "meeting_code, platform, description, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, 'tencent', ?, now(), now())",
                request.title().trim(), request.projectId(), userid, startAt, duration,
                request.joinUrl().trim(), code, desc);
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);

        int sent = fanout(id, request.title().trim(), startAt, request.joinUrl().trim(), request.projectId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("notified", sent);
        return ApiResponse.ok(result);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Void> update(Principal principal, @PathVariable long id, @Valid @RequestBody SaveMeetingRequest request) {
        long userid = currentUserid(principal);
        Map<String, Object> row = load(id);
        requireMeetingWrite(userid, row);

        LocalDateTime startAt = request.startAt() == null ? LocalDateTime.now() : request.startAt();
        int duration = request.durationMinutes() == null || request.durationMinutes() <= 0 ? 60 : request.durationMinutes();
        jdbc.update(
                "update meetings set title = ?, start_at = ?, duration_minutes = ?, join_url = ?, meeting_code = ?, "
                        + "description = ?, updated_at = now() where id = ?",
                request.title().trim(), startAt, duration, request.joinUrl().trim(),
                request.meetingCode() == null ? "" : request.meetingCode().trim(),
                request.description() == null ? "" : request.description().trim(), id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(Principal principal, @PathVariable long id) {
        long userid = currentUserid(principal);
        Map<String, Object> row = load(id);
        requireMeetingWrite(userid, row);
        jdbc.update("update meetings set deleted_at = now(), updated_at = now() where id = ?", id);
        return ApiResponse.ok(null);
    }

    /** 给参会人写站内通知（项目会议 → 项目成员；全公司会议 → 所有人）。 */
    private int fanout(long meetingId, String title, LocalDateTime startAt, String joinUrl, Long projectId) {
        List<Long> receivers;
        if (projectId == null) {
            receivers = jdbc.queryForList("select userid from users where deleted_at is null", Long.class);
        } else {
            receivers = jdbc.queryForList(
                    "select userid from project_users where project_id = ?", Long.class, projectId);
        }
        String payload = startAt.toString().replace("T", " ").substring(0, 16) + " · " + joinUrl;
        int sent = 0;
        for (Long receiver : receivers) {
            jdbc.update(
                    "insert into notifications (userid, type, title, payload, created_at) values (?, 'meeting', ?, ?, now())",
                    receiver, "会议：" + title, payload);
            sent++;
        }
        return sent;
    }

    private Map<String, Object> load(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, project_id, owner_userid from meetings where id = ? and deleted_at is null", id);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "会议不存在");
        }
        return rows.get(0);
    }

    private void requireMeetingWrite(long userid, Map<String, Object> row) {
        if (systemAdmin.isAdmin(userid)) {
            return;
        }
        Object owner = row.get("owner_userid");
        if (owner != null && Long.parseLong(owner.toString()) == userid) {
            return;
        }
        Object projectId = row.get("project_id");
        if (projectId != null) {
            requireProjectWrite(userid, Long.parseLong(projectId.toString()));
            return;
        }
        throw new BusinessException(403, "只有发起人或管理员可以修改会议");
    }

    private void requireProjectVisible(long userid, long projectId) {
        if (systemAdmin.isAdmin(userid)) {
            return;
        }
        Long count = jdbc.queryForObject(
                "select count(*) from project_users where project_id = ? and userid = ?", Long.class, projectId, userid);
        if (count == null || count == 0) {
            throw new BusinessException(403, "无权查看该项目");
        }
    }

    private void requireProjectWrite(long userid, long projectId) {
        if (systemAdmin.isAdmin(userid)) {
            return;
        }
        Long owner = jdbc.queryForObject(
                "select userid from projects where id = ? and deleted_at is null", Long.class, projectId);
        if (owner != null && owner == userid) {
            return;
        }
        List<String> roles = jdbc.queryForList(
                "select coalesce(role, 'member') from project_users where project_id = ? and userid = ?",
                String.class, projectId, userid);
        String role = roles.isEmpty() ? "" : roles.get(0);
        if ("owner".equals(role) || "admin".equals(role) || "member".equals(role)) {
            return;
        }
        throw new BusinessException(403, "无权在该项目下创建会议");
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
