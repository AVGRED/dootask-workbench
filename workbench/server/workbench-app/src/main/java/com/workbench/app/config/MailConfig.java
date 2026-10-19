package com.workbench.app.config;

import com.workbench.common.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {
}
