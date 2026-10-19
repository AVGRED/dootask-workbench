package com.workbench.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 邮件配置：未启用时不发信，只写日志（降级）。 */
@ConfigurationProperties(prefix = "workbench.mail")
public class MailProperties {

    private boolean enabled = false;
    private String host = "";
    private int port = 25;
    private String username = "";
    private String password = "";
    private String from = "no-reply@workbench.local";
    private String baseUrl = "http://localhost:5180";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
