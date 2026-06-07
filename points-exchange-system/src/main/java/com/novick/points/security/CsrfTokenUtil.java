package com.novick.points.security;

import java.security.SecureRandom;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * CSRF token 工具类 — 生成和验证 CSRF token，防止跨站请求伪造。
 */
public final class CsrfTokenUtil {

    private static final String SESSION_ATTR = "CSRF_TOKEN";
    private static final String HEADER_NAME = "X-CSRF-Token";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CsrfTokenUtil() {
    }

    /**
     * 获取当前 session 的 CSRF token，如果不存在则生成新的。
     */
    public static String getOrCreateToken(HttpSession session) {
        String token = (String) session.getAttribute(SESSION_ATTR);
        if (token == null) {
            byte[] bytes = new byte[32];
            SECURE_RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(SESSION_ATTR, token);
        }
        return token;
    }

    /**
     * 验证请求中的 CSRF token 是否与 session 中存储的一致。
     */
    public static boolean isValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        String expected = (String) session.getAttribute(SESSION_ATTR);
        if (expected == null) {
            return false;
        }
        // 从 header 或 parameter 中获取 token
        String actual = request.getHeader(HEADER_NAME);
        if (actual == null || actual.isEmpty()) {
            actual = request.getParameter("_csrf");
        }
        return expected.equals(actual);
    }

    public static String getHeaderName() {
        return HEADER_NAME;
    }
}
