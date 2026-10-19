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

    public JwtService(
            @Value("${workbench.jwt.secret}") String secret,
            @Value("${workbench.jwt.ttl:PT12H}") Duration ttl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    /** 签发令牌。 */
    public String issue(long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
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
