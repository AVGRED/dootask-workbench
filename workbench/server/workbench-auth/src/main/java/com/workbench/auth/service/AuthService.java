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
import org.springframework.dao.DataAccessException;
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

    private static final int LOGIN_MAX_FAILURES = 5;
    private static final long LOGIN_WINDOW_MS = 15 * 60 * 1000L;
    private final java.util.Map<String, java.util.Deque<Long>> loginFailures = new java.util.concurrent.ConcurrentHashMap<>();

    private void checkLoginRate(String username) {
        String key = username == null ? "" : username.toLowerCase(java.util.Locale.ROOT);
        java.util.Deque<Long> attempts = loginFailures.get(key);
        if (attempts == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() > LOGIN_WINDOW_MS) {
                attempts.pollFirst();
            }
            if (attempts.size() >= LOGIN_MAX_FAILURES) {
                throw new BusinessException(429, "登录失败次数过多，请 15 分钟后再试");
            }
        }
    }

    private void recordLoginFailure(String username) {
        String key = username == null ? "" : username.toLowerCase(java.util.Locale.ROOT);
        java.util.Deque<Long> attempts = loginFailures.computeIfAbsent(key,
                ignored -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        synchronized (attempts) {
            attempts.addLast(now);
            while (!attempts.isEmpty() && now - attempts.peekFirst() > LOGIN_WINDOW_MS) {
                attempts.pollFirst();
            }
            while (attempts.size() > LOGIN_MAX_FAILURES) {
                attempts.pollFirst();
            }
        }
    }

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        checkLoginRate(request.username());
        List<Map<String, Object>> rows = findByEmail(request.username());
        if (rows.isEmpty()) {
            recordLoginFailure(request.username());
            throw new BusinessException("账号或密码错误");
        }
        Map<String, Object> user = rows.get(0);
        long userid = ((Number) user.get("userid")).longValue();
        String stored = (String) user.get("password");
        String salt = (String) user.get("encrypt");
        if (!matches(request.password(), stored, salt)) {
            recordLoginFailure(request.username());
            throw new BusinessException("账号或密码错误");
        }
        loginFailures.remove(request.username() == null ? "" : request.username().toLowerCase(java.util.Locale.ROOT));
        if (isBcrypt(stored)) {
            // already upgraded
        } else {
            upgradePassword(userid, request.password());
        }
        String email = (String) user.get("email");
        String nickname = (String) user.get("nickname");
        String role = roleOf(userid);
        Integer tokenVersion = jdbcTemplate.queryForObject(
                "select token_version from users where userid = ?", Integer.class, userid);
        String token = jwtService.issue(userid, email, role, tokenVersion == null ? 0 : tokenVersion);
        return new LoginResponse(token, userid, email, nickname, role, 12 * 3600L);
    }

    /** 角色存于 users.role；列不存在（旧库）或为空时按 staff 处理。 */
    private String roleOf(long userid) {
        try {
            String role = jdbcTemplate.queryForObject("select role from users where userid = ?", String.class, userid);
            return role == null || role.isBlank() ? "staff" : role;
        } catch (RuntimeException exception) {
            return "staff";
        }
    }

    /**
     * 旧库 {@code users} 表没有 {@code deleted_at} 列（软删是后来新增的语义），
     * 因此先按带软删过滤的语句查询，遇到未知列时退回不带过滤的语句。
     */
    private List<Map<String, Object>> findByEmail(String email) {
        String base = "select userid, email, nickname, password, encrypt from users where email = ?";
        try {
            return jdbcTemplate.queryForList(base + " and deleted_at is null limit 1", email);
        } catch (DataAccessException exception) {
            return jdbcTemplate.queryForList(base + " limit 1", email);
        }
    }

    private boolean matches(String raw, String stored, String salt) {
        if (stored == null || stored.isEmpty()) return false;
        if (isBcrypt(stored)) return passwordEncoder.matches(raw, stored);
        String legacy = md5(raw + (salt == null ? "" : salt));
        return legacy.equalsIgnoreCase(stored);
    }

    /** 登出：递增 token 版本，使该用户所有已签发令牌失效。 */
    public void logout(long userid) {
        jdbcTemplate.update("update users set token_version = token_version + 1 where userid = ?", userid);
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
