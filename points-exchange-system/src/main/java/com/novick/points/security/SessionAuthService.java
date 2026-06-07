package com.novick.points.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.stereotype.Component;

import com.novick.points.common.BusinessException;
import com.novick.points.domain.UserRole;

@Component
public class SessionAuthService {

    public static final String SESSION_KEY = "POINTS_LOGIN_USER";

    /**
     * 登录并设置 Session，防止 Session 固定攻击：先 invalidate 旧 session，再创建新 session。
     */
    public void login(HttpServletRequest request, SessionPrincipal principal) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute(SESSION_KEY, principal);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public SessionPrincipal requireLogin(HttpSession session) {
        SessionPrincipal principal = currentUser(session);
        if (principal == null) {
            throw new BusinessException("请先登录");
        }
        return principal;
    }

    public SessionPrincipal requireRole(HttpSession session, UserRole role) {
        SessionPrincipal principal = requireLogin(session);
        if (principal.getRole() != role) {
            throw new BusinessException("无权限访问");
        }
        return principal;
    }

    public SessionPrincipal currentUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof SessionPrincipal) {
            return (SessionPrincipal) value;
        }
        return null;
    }
}
