package com.workbench.auth.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从 {@code Authorization: Bearer <token>} 解析身份并写入 SecurityContext。
 *
 * <p>解析失败时不直接拒绝，交由后续授权规则决定（公开接口可匿名访问）。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private boolean tokenVersionMatches(Claims claims) {
        try {
            Integer version = claims.get("ver", Integer.class);
            if (version == null) {
                version = 0;
            }
            Integer current = jdbcTemplate.queryForObject(
                    "select token_version from users where userid = ? and deleted_at is null",
                    Integer.class, Long.parseLong(claims.getSubject()));
            return current != null && current.equals(version);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Claims claims = jwtService.parse(header.substring(7).trim());
            if (claims != null && tokenVersionMatches(claims)) {
                String role = claims.get("role", String.class);
                List<SimpleGrantedAuthority> authorities = role == null || role.isBlank()
                        ? List.of()
                        : List.of(new SimpleGrantedAuthority("ROLE_" + role));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}
