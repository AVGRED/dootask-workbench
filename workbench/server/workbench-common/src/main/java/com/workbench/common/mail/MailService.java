package com.workbench.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送：未启用 SMTP 或发送失败时**只记日志不抛异常**，业务不受影响。
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final MailProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;

    public MailService(MailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    /** 返回是否真正投递；禁用了邮件通道时返回 false。 */
    public boolean send(String to, String subject, String body) {
        if (!properties.isEnabled()) {
            log.info("mail disabled, skip -> {} | {}", to, subject);
            return false;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("mail enabled but no JavaMailSender bean, skip -> {} | {}", to, subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.getFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return true;
        } catch (RuntimeException exception) {
            log.warn("send mail failed -> {} | {}", to, subject, exception);
            return false;
        }
    }

    public String mode() {
        return properties.isEnabled() ? "smtp" : "disabled";
    }

    public String baseUrl() {
        return properties.getBaseUrl();
    }
}
