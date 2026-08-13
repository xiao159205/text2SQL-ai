package com.xym.antifraud.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 为每个 HTTP 请求补充 traceId，并记录入口、状态码和耗时。 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long startedAt = System.nanoTime();
        MDC.put(TRACE_ID, traceId);
        try {
            response.setHeader("X-Trace-Id", traceId);
            log.info("[请求开始] method={}, uri={}, remoteAddress={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("[请求结束] method={}, uri={}, status={}, elapsedMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            MDC.remove(TRACE_ID);
        }
    }
}
