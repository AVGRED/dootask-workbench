package com.workbench.common.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class MailServiceTest {

    @Test
    void disabledByDefaultAndLogsInstead() {
        MailProperties properties = new MailProperties();
        MailService service = new MailService(properties,
                new StaticListableBeanFactory().getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class));

        assertFalse(service.send("a@b.com", "主题", "正文"));
        assertEquals("disabled", service.mode());
    }

    @Test
    void enabledWithoutSenderFallsBackToLog() {
        MailProperties properties = new MailProperties();
        properties.setEnabled(true);
        MailService service = new MailService(properties,
                new StaticListableBeanFactory().getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class));

        assertFalse(service.send("a@b.com", "主题", "正文"));
        assertEquals("smtp", service.mode());
    }

    @Test
    void baseUrlIsConfigurable() {
        MailProperties properties = new MailProperties();
        properties.setBaseUrl("https://workbench.example.com");
        MailService service = new MailService(properties,
                new StaticListableBeanFactory().getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class));

        assertEquals("https://workbench.example.com", service.baseUrl());
    }
}
