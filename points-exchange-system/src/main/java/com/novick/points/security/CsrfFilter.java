package com.novick.points.security;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF 过滤器 — 对所有修改型 API 请求验证 CSRF token。
 * 排除认证端点（登录/登出）和只读请求（GET/HEAD/OPTIONS）。
 */
@Component
public class CsrfFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public CsrfFilter(@Value("${app.csrf-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String uri = request.getRequestURI();

        // 只读请求不验证 CSRF
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // 排除认证端点
        if (isExcluded(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // 只保护 /api/ 路径
        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // 验证 CSRF token
        if (!CsrfTokenUtil.isValid(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"CSRF token 无效，请刷新页面重试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isExcluded(String uri) {
        return uri.startsWith("/api/auth/login")
                || uri.startsWith("/api/auth/sms-login")
                || uri.startsWith("/api/auth/sms-code")
                || uri.startsWith("/api/auth/logout")
                || uri.startsWith("/api/auth/me")
                || uri.startsWith("/api/auth/csrf-token");
    }
}
