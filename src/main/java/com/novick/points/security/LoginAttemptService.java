package com.novick.points.security;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.novick.points.common.BusinessException;

/**
 * 登录尝试限流服务，防止暴力破解攻击。
 * 基于用户名进行频率限制：5 分钟内最多 5 次失败尝试。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 5 * 60 * 1000; // 5 分钟

    private final ConcurrentHashMap<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    /**
     * 检查指定用户名是否允许登录尝试。
     * 如果超过最大失败次数且在锁定期内，则抛出异常。
     */
    public void checkLoginAllowed(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        AttemptInfo info = attempts.get(username);
        if (info == null) {
            return;
        }
        if (info.attemptCount >= MAX_ATTEMPTS) {
            long elapsed = System.currentTimeMillis() - info.firstAttemptTime;
            if (elapsed < LOCK_DURATION_MS) {
                long remainingSeconds = (LOCK_DURATION_MS - elapsed) / 1000;
                throw new BusinessException("登录尝试次数过多，请 " + remainingSeconds + " 秒后重试");
            } else {
                // 锁定时间已过，清除记录
                attempts.remove(username);
            }
        }
    }

    /**
     * 记录一次登录失败。
     */
    public void loginFailed(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        attempts.compute(username, (key, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null) {
                return new AttemptInfo(now, 1);
            }
            long elapsed = now - existing.firstAttemptTime;
            if (elapsed >= LOCK_DURATION_MS) {
                // 超过时间窗口，重新开始计数
                return new AttemptInfo(now, 1);
            }
            existing.attemptCount++;
            return existing;
        });
    }

    /**
     * 登录成功后清除失败计数。
     */
    public void loginSucceeded(String username) {
        if (username != null) {
            attempts.remove(username);
        }
    }

    private static class AttemptInfo {
        final long firstAttemptTime;
        int attemptCount;

        AttemptInfo(long firstAttemptTime, int attemptCount) {
            this.firstAttemptTime = firstAttemptTime;
            this.attemptCount = attemptCount;
        }
    }
}
