package com.workbench.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成/透传 traceId，并记录耗时：
 *
 * <ul>
 *   <li>请求头 {@code X-Trace-Id} 有值时沿用（便于网关/前端串联），否则生成 16 位；</li>
 *   <li>写入 MDC，日志 pattern 里用 {@code %X{traceId}} 输出；</li>
 *   <li>响应头回写 {@code X-Trace-Id}，前端与工单可直接引用；</li>
 *   <li>业务异常响应里的 {@code traceId} 字段也来自同一个 MDC；</li>
 *   <li>请求结束记录「方法 路径 -&gt; 状态 耗时」，健康检查走 DEBUG 避免刷屏。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final long SLOW_THRESHOLD_MS = 300;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - started) / 1_000_000;
            boolean quiet = request.getRequestURI().startsWith("/actuator");
            if (!quiet || costMs >= SLOW_THRESHOLD_MS) {
                log.info("{} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), costMs);
            } else {
                log.debug("{} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), costMs);
            }
            MDC.remove(MDC_KEY);
        }
    }
}
