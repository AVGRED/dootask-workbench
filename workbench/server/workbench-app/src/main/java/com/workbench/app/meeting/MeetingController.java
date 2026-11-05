package com.workbench.app.meeting;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * 会议：管理员创建并指定参会人，参会人会收到站内通知，点链接进入腾讯会议。
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
            LocalDateTime startAt,
            Integer durationMinutes,
            @NotBlank(message = "请填写会议链接") @Size(max = 600) String joinUrl,
            @Size(max = 40) String meetingCode,
            @Size(max = 500) String description,
            @NotEmpty(message = "请选择参会人") List<Long> participantUserids
    ) {
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(Principal principal,
                                                       @RequestParam(required = false) Integer limit) {
        long userid = currentUserid(principal);
        int size = limit == null || limit <= 0 || limit > 200 ? 100 : limit;
        boolean admin = systemAdmin.isAdmin(userid);

        String sql = "select m.id, m.title, m.owner_userid, m.start_at, m.duration_minutes, m.join_url, "
                + "m.meeting_code, m.platform, m.description, m.created_at, coalesce(u.nickname, '') as owner_name "
                + "from meetings m left join users u on u.userid = m.owner_userid "
                + "where m.deleted_at is null ";
        List<Object> args = new ArrayList<>();
        if (!admin) {
            sql += "and exists (select 1 from meeting_participants p where p.meeting_id = m.id and p.userid = ?) ";
            args.add(userid);
        }
        sql += "order by m.start_at desc, m.id desc limit ?";
        args.add(size);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, args.toArray())) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            long meetingId = Long.parseLong(row.get("id").toString());
            List<Map<String, Object>> people = jdbc.queryForList(
                    "select p.userid, coalesce(u.nickname, '') as nickname from meeting_participants p "
                            + "left join users u on u.userid = p.userid where p.meeting_id = ? order by p.userid",
                    meetingId);
            List<Long> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Map<String, Object> person : people) {
                ids.add(Long.parseLong(person.get("userid").toString()));
                names.add(String.valueOf(person.get("nickname")));
            }
            item.put("participant_userids", ids);
            item.put("participant_names", names);
            item.put("participant_count", ids.size());
            rows.add(item);
        }
        return ApiResponse.ok(rows);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(Principal principal, @Valid @RequestBody SaveMeetingRequest request) {
        long userid = currentUserid(principal);
        systemAdmin.requireAdmin(userid);

        LocalDateTime startAt = request.startAt() == null ? LocalDateTime.now() : request.startAt();
        int duration = Math.min(
                request.durationMinutes() == null || request.durationMinutes() <= 0 ? 60 : request.durationMinutes(), 720);
        String joinUrl = safeJoinUrl(request.joinUrl());
        String code = request.meetingCode() == null ? "" : request.meetingCode().trim();
        String desc = request.description() == null ? "" : request.description().trim();
        List<Long> participants = validParticipants(request.participantUserids());

        jdbc.update(
                "insert into meetings (title, owner_userid, start_at, duration_minutes, join_url, meeting_code, "
                        + "platform, description, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, 'tencent', ?, now(), now())",
                request.title().trim(), userid, startAt, duration, joinUrl, code, desc);
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        saveParticipants(id, participants);
        int sent = notify(participants, request.title().trim(), startAt, joinUrl);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("notified", sent);
        return ApiResponse.ok(result);
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<Map<String, Object>> update(Principal principal, @PathVariable long id,
                                                   @Valid @RequestBody SaveMeetingRequest request) {
        long userid = currentUserid(principal);
        systemAdmin.requireAdmin(userid);
        requireMeeting(id);

        LocalDateTime startAt = request.startAt() == null ? LocalDateTime.now() : request.startAt();
        int duration = Math.min(
                request.durationMinutes() == null || request.durationMinutes() <= 0 ? 60 : request.durationMinutes(), 720);
        String joinUrl = safeJoinUrl(request.joinUrl());
        List<Long> participants = validParticipants(request.participantUserids());

        jdbc.update(
                "update meetings set title = ?, start_at = ?, duration_minutes = ?, join_url = ?, meeting_code = ?, "
                        + "description = ?, updated_at = now() where id = ?",
                request.title().trim(), startAt, duration, joinUrl,
                request.meetingCode() == null ? "" : request.meetingCode().trim(),
                request.description() == null ? "" : request.description().trim(), id);

        List<Long> before = jdbc.queryForList(
                "select userid from meeting_participants where meeting_id = ?", Long.class, id);
        jdbc.update("delete from meeting_participants where meeting_id = ?", id);
        saveParticipants(id, participants);

        // 只给新增的参会人发通知，避免重复打扰
        List<Long> added = new ArrayList<>(participants);
        added.removeAll(before);
        int sent = notify(added, request.title().trim(), startAt, joinUrl);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notified", sent);
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(Principal principal, @PathVariable long id) {
        systemAdmin.requireAdmin(currentUserid(principal));
        requireMeeting(id);
        jdbc.update("update meetings set deleted_at = now(), updated_at = now() where id = ?", id);
        return ApiResponse.ok(null);
    }

    private List<Long> validParticipants(List<Long> raw) {
        List<Long> ids = new ArrayList<>();
        for (Long id : raw) {
            if (id == null || ids.contains(id)) {
                continue;
            }
            Long exists = jdbc.queryForObject(
                    "select count(*) from users where userid = ? and deleted_at is null", Long.class, id);
            if (exists != null && exists > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException("请选择至少一位参会人");
        }
        return ids;
    }

    private void saveParticipants(long meetingId, List<Long> participants) {
        for (Long participant : participants) {
            jdbc.update(
                    "insert into meeting_participants (meeting_id, userid, created_at) values (?, ?, now())",
                    meetingId, participant);
        }
    }

    private int notify(List<Long> receivers, String title, LocalDateTime startAt, String joinUrl) {
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

    private void requireMeeting(long id) {
        Long count = jdbc.queryForObject(
                "select count(*) from meetings where id = ? and deleted_at is null", Long.class, id);
        if (count == null || count == 0) {
            throw new BusinessException(404, "会议不存在");
        }
    }

    private String safeJoinUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException("会议链接必须以 http:// 或 https:// 开头");
        }
        return url;
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
