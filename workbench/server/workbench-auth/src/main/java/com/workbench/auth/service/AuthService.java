package com.workbench.auth.service;

import com.workbench.auth.jwt.JwtService;
import com.workbench.auth.web.LoginRequest;
import com.workbench.auth.web.LoginResponse;
import com.workbench.common.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录服务。
 *
 * <p>旧库密码是 {@code md5(明文 + encrypt 盐)}（{@code password} 列只有 50 字符，装不下 BCrypt），
 * 因此这里做兼容校验：先按旧规则比对，成功后立刻用 BCrypt 重写并清空盐列，实现平滑升级。</p>
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
                "select userid, email, nickname, password, encrypt from users where email = ? and deleted_at is null limit 1",
                request.username());
        if (rows.isEmpty()) {
            throw new BusinessException("账号或密码错误");
        }
        Map<String, Object> user = rows.get(0);
        long userid = ((Number) user.get("userid")).longValue();
        String stored = (String) user.get("password");
        String salt = (String) user.get("encrypt");
        if (!matches(request.password(), stored, salt)) {
            throw new BusinessException("账号或密码错误");
        }
        if (isBcrypt(stored)) {
            // already upgraded
        } else {
            upgradePassword(userid, request.password());
        }
        String email = (String) user.get("email");
        String nickname = (String) user.get("nickname");
        String token = jwtService.issue(userid, email, "staff");
        return new LoginResponse(token, userid, email, nickname, "staff", 12 * 3600L);
    }

    private boolean matches(String raw, String stored, String salt) {
        if (stored == null || stored.isEmpty()) return false;
        if (isBcrypt(stored)) return passwordEncoder.matches(raw, stored);
        String legacy = md5(raw + (salt == null ? "" : salt));
        return legacy.equalsIgnoreCase(stored);
    }

    private boolean isBcrypt(String stored) {
        return stored != null && stored.startsWith("$2");
    }

    private void upgradePassword(long userid, String raw) {
        try {
            jdbcTemplate.update("update users set password = ?, encrypt = '' where userid = ?",
                    passwordEncoder.encode(raw), userid);
        } catch (RuntimeException ignored) {
            // 升级失败不影响本次登录，下次登录会再次尝试
        }
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
