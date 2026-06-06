package com.novick.points.security;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.novick.points.domain.UserRole;

@Component
public class SessionAuthInterceptor implements HandlerInterceptor {

    private final SessionAuthService sessionAuthService;

    public SessionAuthInterceptor(SessionAuthService sessionAuthService) {
        this.sessionAuthService = sessionAuthService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        HttpSession session = request.getSession(false);
        SessionPrincipal principal = session == null ? null : sessionAuthService.currentUser(session);

        if (requiresAdmin(uri)) {
            if (principal == null || principal.getRole() != UserRole.ADMIN) {
                return reject(request, response, "/admin/login.html");
            }
        } else if (requiresUser(uri)) {
            if (principal == null) {
                return reject(request, response, "/app/login.html");
            }
        }
        return true;
    }

    private boolean requiresAdmin(String uri) {
        return uri.startsWith("/admin/") || uri.startsWith("/api/admin/");
    }

    private boolean requiresUser(String uri) {
        return uri.startsWith("/app/") || uri.startsWith("/api/app/");
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, String loginPath) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"请先登录\"}");
        } else {
            response.sendRedirect(loginPath);
        }
        return false;
    }
}
