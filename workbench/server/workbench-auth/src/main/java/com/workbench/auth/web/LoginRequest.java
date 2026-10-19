package com.workbench.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * @param username 用户名或邮箱
 * @param password 明文密码，服务端用 BCrypt 校验
 */
public record LoginRequest(
        @NotBlank(message = "请输入账号") @Size(max = 64) String username,
        @NotBlank(message = "请输入密码") @Size(max = 128) String password
) {
}
