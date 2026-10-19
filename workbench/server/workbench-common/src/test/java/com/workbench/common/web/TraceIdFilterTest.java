package com.workbench.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    @Test
    void generatesTraceIdAndWritesHeader() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seenInMdc = new String[1];

        filter.doFilter(request, response, (req, res) -> seenInMdc[0] = MDC.get(TraceIdFilter.MDC_KEY));

        String header = response.getHeader(TraceIdFilter.HEADER);
        assertTrue(header != null && header.length() == 16, "traceId 应为 16 位");
        assertEquals(header, seenInMdc[0], "MDC 与响应头应一致");
        assertEquals(null, MDC.get(TraceIdFilter.MDC_KEY), "请求结束后应清理 MDC");
    }

    @Test
    void reusesIncomingTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tasks");
        request.addHeader(TraceIdFilter.HEADER, "upstream-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals("upstream-trace-123", response.getHeader(TraceIdFilter.HEADER));
    }

    @Test
    void replacesOverlongTraceId() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(TraceIdFilter.HEADER, "a".repeat(200));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertNotEquals("a".repeat(200), response.getHeader(TraceIdFilter.HEADER));
        assertEquals(16, response.getHeader(TraceIdFilter.HEADER).length());
    }
}
