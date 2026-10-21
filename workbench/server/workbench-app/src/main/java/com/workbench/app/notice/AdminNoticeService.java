package com.workbench.app.notice;

import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员发布通知：写一条 admin_notices 记录，并给每个在职员工写一条站内通知。
 */
@Service
public class AdminNoticeService {

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;

    public AdminNoticeService(JdbcTemplate jdbc, SystemAdmin systemAdmin) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
    }

    @Transactional
    public Map<String, Object> publish(long currentUserid, String title, String content) {
        systemAdmin.requireAdmin(currentUserid);
        String safeTitle = title == null ? "" : title.trim();
        String safeContent = content == null ? "" : content.trim();
        if (safeTitle.isEmpty()) {
            throw new BusinessException("通知标题不能为空");
        }
        if (safeTitle.length() > 120) {
            throw new BusinessException("标题最多 120 字");
        }
        if (safeContent.length() > 1000) {
            throw new BusinessException("内容最多 1000 字");
        }
        jdbc.update(
                "insert into admin_notices (title, content, created_by, created_at) values (?, ?, ?, now())",
                safeTitle, safeContent, currentUserid);
        Long noticeId = jdbc.queryForObject("select last_insert_id()", Long.class);

        List<Long> receivers = jdbc.queryForList(
                "select userid from users where deleted_at is null", Long.class);
        int sent = 0;
        for (Long userid : receivers) {
            jdbc.update(
                    "insert into notifications (userid, type, title, payload, created_at) "
                            + "values (?, 'notice', ?, ?, now())",
                    userid, safeTitle, safeContent);
            sent++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", noticeId);
        result.put("receivers", sent);
        return result;
    }

    public List<Map<String, Object>> recent() {
        return jdbc.queryForList(
                "select n.id, n.title, n.content, n.created_by, n.created_at, "
                        + "coalesce(u.nickname, '') as created_by_name "
                        + "from admin_notices n left join users u on u.userid = n.created_by "
                        + "order by n.id desc limit 50");
    }
}
