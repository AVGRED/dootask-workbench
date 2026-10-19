package com.workbench.task.schedule;

import com.workbench.common.mail.MailService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 到期提醒：每天 09:00 汇总「已逾期 + 24 小时内到期」的未完成任务，按负责人各发一封邮件。
 *
 * <p>邮件通道未启用时只写日志，不影响任何业务。</p>
 */
@Component
public class TaskReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskReminderScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final MailService mailService;

    public TaskReminderScheduler(JdbcTemplate jdbcTemplate, MailService mailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailService = mailService;
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void remindDueTasks() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select t.id, t.name, t.end_at, t.owner_userid, u.email "
                        + "from project_tasks t join users u on u.userid = t.owner_userid "
                        + "where t.deleted_at is null and t.complete_at is null and t.owner_userid > 0 "
                        + "and t.end_at is not null and t.end_at <= date_add(now(), interval 1 day) "
                        + "order by u.email, t.end_at");
        if (rows.isEmpty()) {
            log.info("no due tasks to remind");
            return;
        }
        String currentEmail = null;
        StringBuilder body = new StringBuilder();
        int count = 0;
        for (Map<String, Object> row : rows) {
            String email = String.valueOf(row.get("email"));
            if (currentEmail != null && !currentEmail.equals(email)) {
                flush(currentEmail, body, count);
                body.setLength(0);
                count = 0;
            }
            currentEmail = email;
            body.append("- #").append(row.get("id")).append(' ').append(row.get("name"))
                    .append("（截止 ").append(row.get("end_at")).append("）\n");
            count++;
        }
        if (currentEmail != null) {
            flush(currentEmail, body, count);
        }
    }

    private void flush(String email, StringBuilder body, int count) {
        mailService.send(email, "工作台任务到期提醒（" + count + "）", body.toString());
    }
}
