package com.novick.points.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.novick.points.common.BusinessException;
import com.novick.points.config.AppProperties;

@Service
public class SmsCodeService {

    private final AppProperties appProperties;
    private final ConcurrentMap<String, SmsCodeRecord> codeStore = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public SmsCodeService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Map<String, Object> sendCode(String phoneNumber) {
        long now = Instant.now().getEpochSecond();
        SmsCodeRecord existing = codeStore.get(phoneNumber);
        if (existing != null && existing.getNextSendAt() > now) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        SmsCodeRecord record = new SmsCodeRecord(code, now + appProperties.getSmsCodeTtlSeconds(),
                now + appProperties.getSmsResendIntervalSeconds());
        codeStore.put(phoneNumber, record);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phoneNumber", phoneNumber);
        result.put("expiresIn", appProperties.getSmsCodeTtlSeconds());
        result.put("mock", appProperties.isSmsMockEnabled());
        if (appProperties.isSmsMockEnabled()) {
            result.put("debugCode", code);
        }
        return result;
    }

    public void verifyCode(String phoneNumber, String code) {
        SmsCodeRecord record = codeStore.get(phoneNumber);
        long now = Instant.now().getEpochSecond();
        if (record == null || record.getExpiresAt() < now) {
            codeStore.remove(phoneNumber);
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!record.getCode().equals(code)) {
            throw new BusinessException("验证码错误");
        }
        codeStore.remove(phoneNumber);
    }

    private static class SmsCodeRecord {
        private final String code;
        private final long expiresAt;
        private final long nextSendAt;

        private SmsCodeRecord(String code, long expiresAt, long nextSendAt) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.nextSendAt = nextSendAt;
        }

        public String getCode() {
            return code;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public long getNextSendAt() {
            return nextSendAt;
        }
    }
}
