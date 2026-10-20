package com.workbench.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 签发与校验。
 *
 * <p>令牌只承载用户标识与角色，权限判断仍以数据库为准，避免令牌过期前权限变更失效。</p>
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    private static final String DEFAULT_SECRET = "change-me-in-production-please-use-32-bytes-min";

    /** 拒绝默认/过短密钥，避免带着示例配置上线。 */
    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("必须通过 JWT_SECRET 配置签名密钥");
        }
        if (DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET 仍是默认示例值，请改成至少 32 字符的随机串后再启动（例如 openssl rand -base64 48）");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字符，请使用随机长串");
        }
    }

    public JwtService(
            @Value("${workbench.jwt.secret}") String secret,
            @Value("${workbench.jwt.ttl:PT12H}") Duration ttl) {
        validateSecret(secret);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    /** 签发令牌。 */
    public String issue(long userId, String username, String role) {
        return issue(userId, username, role, 0);
    }

    /** 签发带 token 版本号的令牌；版本号变化后旧令牌自动失效。 */
    public String issue(long userId, String username, String role, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("ver", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析令牌。
     *
     * @return 载荷；令牌无效或过期时返回 {@code null}
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public long userId(String token) {
        Claims claims = parse(token);
        return claims == null ? 0L : Long.parseLong(claims.getSubject());
    }
}
