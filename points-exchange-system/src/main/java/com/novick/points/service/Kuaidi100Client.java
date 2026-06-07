package com.novick.points.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kuaidi100.sdk.api.QueryTrack;
import com.kuaidi100.sdk.core.IBaseClient;
import com.kuaidi100.sdk.pojo.HttpResult;
import com.kuaidi100.sdk.request.QueryTrackReq;
import com.kuaidi100.sdk.request.QueryTrackParam;
import com.kuaidi100.sdk.utils.SignUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class Kuaidi100Client {

    private static final Logger log = LoggerFactory.getLogger(Kuaidi100Client.class);

    @Value("${kuaidi100.key:}")
    private String key;

    @Value("${kuaidi100.customer:}")
    private String customer;

    private final Gson gson = new Gson();
    private final IBaseClient queryTrack = new QueryTrack();

    public Map<String, Object> queryTracking(String carrier, String trackingNo) {
        if (key == null || key.isEmpty() || customer == null || customer.isEmpty()) {
            throw new RuntimeException("请配置快递100密钥");
        }

        try {
            QueryTrackReq queryTrackReq = new QueryTrackReq();
            QueryTrackParam queryTrackParam = new QueryTrackParam();
            
            // 标准化快递公司编码
            String normalizedCom = normalizeCompanyCode(carrier);
            queryTrackParam.setCom(normalizedCom);
            queryTrackParam.setNum(trackingNo);
            // 不设置resultv2，与debug工具保持一致
            queryTrackParam.setShow("0");
            queryTrackParam.setOrder("desc");

            String param = gson.toJson(queryTrackParam);
            queryTrackReq.setParam(param);
            queryTrackReq.setCustomer(customer);
            queryTrackReq.setSign(SignUtils.querySign(param, key, customer));

            HttpResult httpResult = (HttpResult) queryTrack.execute(queryTrackReq);
            String resultStr = httpResult.getBody();
            log.info("快递100查询结果: {}", resultStr);
            
            // 解析返回的JSON字符串
            Map<String, Object> response = gson.fromJson(resultStr, new TypeToken<Map<String, Object>>(){}.getType());
            
            return buildResponse(response, carrier, trackingNo);

        } catch (Exception e) {
            log.error("查询快递失败: carrier={}, trackingNo={}, error={}", carrier, trackingNo, e.getMessage());
            throw new RuntimeException("查询快递失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResponse(Map<String, Object> resp, String carrier, String trackingNo) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 检查是否有错误
        String status = (String) resp.get("status");
        if ("0".equals(status)) {
            result.put("success", false);
            result.put("message", resp.get("message"));
            result.put("com", carrier);
            result.put("nu", trackingNo);
            result.put("state", "");
            result.put("data", new java.util.ArrayList<>());
            return result;
        }
        
        result.put("success", true);
        result.put("com", resp.get("com"));
        result.put("nu", resp.get("nu"));
        result.put("state", resp.get("state"));
        
        // 解析物流轨迹
        Object data = resp.get("data");
        if (data instanceof List) {
            result.put("data", data);
        } else {
            result.put("data", new java.util.ArrayList<>());
        }
        
        result.put("message", resp.get("message"));
        result.put("cachedAt", java.time.LocalDateTime.now().toString());
        return result;
    }

    private String normalizeCompanyCode(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String code = input.trim().toLowerCase(Locale.ROOT);
        // 快递100标准编码映射
        switch (code) {
            // 韵达系列
            case "yunda":
            case "yd":
            case "韵达":
            case "韵达快递":
                return "yunda";
            // 圆通
            case "yuantong":
            case "yt":
            case "圆通":
            case "圆通速递":
                return "yuantong";
            // 中通
            case "zhongtong":
            case "zt":
            case "中通":
            case "中通快递":
                return "zhongtong";
            // 申通
            case "shentong":
            case "st":
            case "申通":
            case "申通快递":
                return "shentong";
            // 顺丰
            case "shunfeng":
            case "sf":
            case "顺丰":
            case "顺丰速运":
                return "shunfeng";
            // 极兔
            case "jtexpress":
            case "jt":
            case "极兔":
            case "极兔速递":
                return "jtexpress";
            // 京东
            case "jd":
            case "jdwl":
            case "京东":
            case "京东物流":
                return "jd";
            // EMS
            case "ems":
            case "邮政ems":
                return "ems";
            // 德邦
            case "debangkuaidi":
            case "db":
            case "德邦":
            case "德邦快递":
                return "debangkuaidi";
            // 百世
            case "huitongkuaidi":
            case "bs":
            case "百世":
            case "百世快递":
                return "huitongkuaidi";
            // 邮政
            case "youzhengguonei":
            case "yz":
            case "邮政":
            case "邮政快递包裹":
                return "youzhengguonei";
            // 安能
            case "annengwuliu":
            case "an":
            case "安能":
            case "安能快运":
                return "annengwuliu";
            default:
                return code;
        }
    }
}
