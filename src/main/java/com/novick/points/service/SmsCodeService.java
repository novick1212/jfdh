package com.novick.points.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.stereotype.Service;

import com.novick.points.common.BusinessException;
import com.novick.points.config.AppProperties;
import org.springframework.util.StringUtils;

@Service
public class SmsCodeService {

    private final AppProperties appProperties;
    private final ConcurrentMap<String, SmsCodeRecord> codeStore = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final Client client;

    public SmsCodeService(AppProperties appProperties) {
        this.appProperties = appProperties;

        if (!appProperties.isSmsMockEnabled()) {
            String accessKeyId = appProperties.getAliyunSmsAccessKeyId();
            String accessKeySecret = appProperties.getAliyunSmsAccessKeySecret();

            if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
                throw new IllegalStateException("阿里云短信配置不完整：AccessKey ID或Secret为空");
            }

            try {
                Config config = new Config()
                        .setAccessKeyId(accessKeyId)
                        .setAccessKeySecret(accessKeySecret)
                        .setEndpoint("dysmsapi.aliyuncs.com")
                        .setReadTimeout(30000)   // 30秒读取超时
                        .setConnectTimeout(10000); // 10秒连接超时
                this.client = new Client(config);
            } catch (Exception e) {
                throw new IllegalStateException("初始化阿里云短信客户端失败: " + e.getMessage(), e);
            }
        } else {
            this.client = null;
        }
    }

    public Map<String, Object> sendCode(String phoneNumber) {
        long now = Instant.now().getEpochSecond();
        SmsCodeRecord existing = codeStore.get(phoneNumber);
        if (existing != null && existing.getNextSendAt() > now) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        if (!appProperties.isSmsMockEnabled()) {
            sendViaAliyun(phoneNumber, code);
        }

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

    private void sendViaAliyun(String phoneNumber, String code) {
        try {
            // 手机号格式：国内手机号需要加86前缀
            String phoneWithCountryCode = phoneNumber;
            if (phoneNumber != null && phoneNumber.matches("^1\\d{10}$")) {
                phoneWithCountryCode = "86" + phoneNumber;
            }
            System.out.println("[SMS] 准备发送短信到: " + phoneWithCountryCode + ", 验证码: " + code);
            System.out.println("[SMS] 签名: " + appProperties.getAliyunSmsSignName() + ", 模板: " + appProperties.getAliyunSmsTemplateCode());
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phoneWithCountryCode)
                    .setSignName(appProperties.getAliyunSmsSignName())
                    .setTemplateCode(appProperties.getAliyunSmsTemplateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}");

            SendSmsResponse response = client.sendSms(request);
            String responseCode = response.getBody() != null ? response.getBody().getCode() : "null";
            String responseMessage = response.getBody() != null ? response.getBody().getMessage() : "null";
            String responseBizId = response.getBody() != null ? response.getBody().getBizId() : "null";
            System.out.println("[SMS] 阿里云响应 - Code: " + responseCode + ", Message: " + responseMessage + ", BizId: " + responseBizId);

            if (!"OK".equals(responseCode)) {
                throw new BusinessException("短信发送失败: " + responseMessage);
            }
        } catch (Exception e) {
            System.err.println("[SMS] 短信发送异常: " + e.getMessage());
            throw new BusinessException("短信发送异常: " + e.getMessage());
        }
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
