package com.workbench.auth.service;

import com.workbench.auth.jwt.JwtService;
import com.workbench.auth.web.LoginRequest;
import com.workbench.auth.web.LoginResponse;
import com.workbench.common.api.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录服务。
 *
 * <p>骨架阶段用 {@link JdbcTemplate} 直接读取旧库 {@code users} 表，等 {@code workbench-user}
 * 落地后改为调用其 Service，避免 auth 依赖 user 实体。</p>
 *
 * <p>待确认：旧表的账号列是 {@code email}；若同时支持用户名登录，需要补 {@code username}
 * 列或账号映射表。</p>
 */
@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select userid, email, nickname, password from users where email = ? and deleted_at is null limit 1",
                request.username());
        if (rows.isEmpty()) {
            throw new BusinessException("账号或密码错误");
        }
        Map<String, Object> user = rows.get(0);
        String encoded = (String) user.get("password");
        if (encoded == null || !passwordEncoder.matches(request.password(), encoded)) {
            throw new BusinessException("账号或密码错误");
        }
        long userid = ((Number) user.get("userid")).longValue();
        String email = (String) user.get("email");
        String nickname = (String) user.get("nickname");
        String token = jwtService.issue(userid, email, "staff");
        return new LoginResponse(token, userid, email, nickname, "staff", 12 * 3600L);
    }
}
