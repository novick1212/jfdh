package com.novick.points.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novick.points.common.BusinessException;

@Component
public class Kuaidi100Client {

    private static final String QUERY_URL = "https://poll.kuaidi100.com/poll/query.do";

    private final Environment env;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public Kuaidi100Client(Environment env, ObjectMapper objectMapper) {
        this.env = env;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public JsonNode query(String companyCode, String trackingNo, String phone) {
        String customer = requiredEnv("KUAIDI100_CUSTOMER");
        String key = requiredEnv("KUAIDI100_KEY");

        String com = normalizeCompanyCode(companyCode);
        String num = trackingNo == null ? "" : trackingNo.trim();
        if (com.isEmpty()) {
            throw new BusinessException("缺少快递公司编码（快递100 com），请后台发货时填写");
        }
        if (num.isEmpty()) {
            throw new BusinessException("缺少快递单号");
        }

        Map<String, Object> paramMap = new LinkedHashMap<>();
        paramMap.put("com", com);
        paramMap.put("num", num);
        if (phone != null && !phone.trim().isEmpty()) {
            paramMap.put("phone", phone.trim());
        }
        paramMap.put("resultv2", env.getProperty("KUAIDI100_RESULTV2", "1"));
        paramMap.put("show", "0");
        paramMap.put("order", "desc");
        paramMap.put("lang", env.getProperty("KUAIDI100_LANG", "zh"));
        if (Boolean.parseBoolean(env.getProperty("KUAIDI100_NEED_COURIER_INFO", "false"))) {
            paramMap.put("needCourierInfo", true);
        }

        try {
            String paramJson = objectMapper.writeValueAsString(paramMap);
            String sign = md5Upper(paramJson + key + customer);
            String body = formBody(Map.of(
                    "customer", customer,
                    "sign", sign,
                    "param", paramJson));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(QUERY_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String text = response.body() == null ? "" : response.body();
            JsonNode root = objectMapper.readTree(text);

            if (root.has("result") && !root.path("result").asBoolean(true)) {
                String message = root.path("message").asText("");
                String code = root.path("returnCode").asText("");
                throw new BusinessException((code.isEmpty() ? "" : code + " ") + (message.isEmpty() ? "查询失败" : message));
            }
            if (root.has("status") && !"200".equals(root.path("status").asText())) {
                String message = root.path("message").asText("");
                throw new BusinessException(message.isEmpty() ? "查询失败" : message);
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("查询物流失败：" + e.getMessage());
        }
    }

    private String requiredEnv(String key) {
        String value = env.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException("缺少环境变量 " + key + "，请在服务器环境变量中配置快递100企业版参数");
        }
        return value.trim();
    }

    private String normalizeCompanyCode(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }

    private String formBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String md5Upper(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}

