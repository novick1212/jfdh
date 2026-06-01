package com.novick.points.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:points-e2e-test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.sms-mock-enabled=true"
})
@AutoConfigureMockMvc
@Transactional
class FullFlowE2ETest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRunFullFlow() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String csv = "姓名,手机号,人力资源码\n张三,13900000001,HR9999\n";
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/admin/users/import-csv")
                        .file(file)
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult usersResult = mockMvc.perform(get("/api/admin/users")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode usersPayload = objectMapper.readTree(usersResult.getResponse().getContentAsString());
        long userId = findUserIdByPhone(usersPayload.path("data"), "13900000001");

        mockMvc.perform(post("/api/admin/users/" + userId + "/redeem-quota")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quota\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.redeemQuota").value(5));

        MvcResult sendCodeResult = mockMvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"张三\",\"phoneNumber\":\"13900000001\",\"hrCode\":\"HR9999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.debugCode").exists())
                .andReturn();
        JsonNode sendCodePayload = objectMapper.readTree(sendCodeResult.getResponse().getContentAsString());
        String debugCode = sendCodePayload.path("data").path("debugCode").asText();

        MockHttpSession userSession = new MockHttpSession();
        mockMvc.perform(post("/api/auth/sms-login")
                        .session(userSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"13900000001\",\"smsCode\":\"" + debugCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phoneNumber").value("13900000001"));

        mockMvc.perform(get("/api/auth/me")
                        .session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.redeemRemaining").value(5));

        MvcResult itemsResult = mockMvc.perform(get("/api/app/items")
                        .session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode itemsPayload = objectMapper.readTree(itemsResult.getResponse().getContentAsString());
        List<Long> itemIds = firstNItemIds(itemsPayload.path("data"), 2);

        String checkoutBody = "{\"items\":[{\"itemId\":" + itemIds.get(0) + ",\"quantity\":1},{\"itemId\":" + itemIds.get(1)
                + ",\"quantity\":2}],\"recipientName\":\"张三\",\"phone\":\"13900000001\",\"address\":\"成都市高新区\"}";
        MvcResult checkoutResult = mockMvc.perform(post("/api/app/orders/checkout")
                        .session(userSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode checkoutPayload = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        if (!checkoutPayload.path("data").isArray() || checkoutPayload.path("data").size() != 2) {
            throw new IllegalStateException("结算应生成 2 条订单");
        }

        mockMvc.perform(get("/api/auth/me")
                        .session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.redeemUsed").value(3))
                .andExpect(jsonPath("$.data.redeemRemaining").value(2));

        MvcResult ordersResult = mockMvc.perform(get("/api/admin/orders")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode ordersPayload = objectMapper.readTree(ordersResult.getResponse().getContentAsString());
        long orderId = firstOrderIdByUserId(ordersPayload.path("data"), userId);

        mockMvc.perform(post("/api/admin/orders/" + orderId + "/fulfill")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingCarrier\":\"顺丰\",\"trackingNo\":\"SF123456789\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingUrl").exists());

        mockMvc.perform(get("/api/app/orders")
                        .session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private long findUserIdByPhone(JsonNode users, String phoneNumber) {
        if (users == null || !users.isArray()) {
            throw new IllegalStateException("用户列表返回格式错误");
        }
        for (JsonNode user : users) {
            if (phoneNumber.equals(user.path("phoneNumber").asText())) {
                return user.path("id").asLong();
            }
        }
        throw new IllegalStateException("未找到手机号对应用户：" + phoneNumber);
    }

    private List<Long> firstNItemIds(JsonNode items, int n) {
        if (items == null || !items.isArray()) {
            throw new IllegalStateException("商品列表返回格式错误");
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : items) {
            ids.add(item.path("id").asLong());
            if (ids.size() >= n) {
                return ids;
            }
        }
        throw new IllegalStateException("可用商品数量不足");
    }

    private long firstOrderIdByUserId(JsonNode orders, long userId) {
        if (orders == null || !orders.isArray()) {
            throw new IllegalStateException("订单列表返回格式错误");
        }
        for (JsonNode order : orders) {
            if (userId == order.path("userId").asLong()) {
                return order.path("id").asLong();
            }
        }
        throw new IllegalStateException("未找到该用户的订单");
    }
}
