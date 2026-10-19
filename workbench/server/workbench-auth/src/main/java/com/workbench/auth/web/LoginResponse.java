package com.workbench.auth.web;

/**
 * 登录响应。
 *
 * @param token      JWT，前端放入 {@code Authorization: Bearer}。
 * @param userid     用户 id
 * @param username   登录名
 * @param nickname   展示名
 * @param role       系统角色，前端据此决定进入哪个端
 * @param expiresIn  有效秒数，前端用于提前续期
 */
public record LoginResponse(
        String token,
        long userid,
        String username,
        String nickname,
        String role,
        long expiresIn
) {
}
