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

        String csv = "姓名,人力资源码\n张三,HR9999\n";
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
        long userId = findUserIdByHrCode(usersPayload.path("data"), "HR9999");

        mockMvc.perform(post("/api/admin/users/" + userId + "/redeem-quota")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quota\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.redeemQuota").value(1));

        MvcResult sendCodeResult = mockMvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"张三\",\"hrCode\":\"HR9999\",\"phoneNumber\":\"13900000001\"}"))
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
                        .content("{\"hrCode\":\"HR9999\",\"phoneNumber\":\"13900000001\",\"smsCode\":\"" + debugCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hrCode").value("HR9999"));

        mockMvc.perform(get("/api/auth/me")
                        .session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.redeemRemaining").value(1));

        MvcResult homeResult = mockMvc.perform(get("/api/app/home")
                        .session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode homePayload = objectMapper.readTree(homeResult.getResponse().getContentAsString());
        long itemId = homePayload.path("data").path("items").get(0).path("id").asLong();

        MvcResult checkoutResult = mockMvc.perform(post("/api/app/orders")
                        .session(userSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + itemId + ",\"quantity\":1,\"recipientName\":\"张三\",\"phone\":\"13900000001\",\"address\":\"成都市高新区\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        JsonNode checkoutPayload = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        if (!checkoutPayload.path("data").isObject()) {
            throw new IllegalStateException("下单返回格式错误");
        }

        mockMvc.perform(get("/api/auth/me")
                        .session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.redeemUsed").value(1))
                .andExpect(jsonPath("$.data.redeemRemaining").value(0));

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
                .andExpect(jsonPath("$.data.shipments").isArray())
                .andExpect(jsonPath("$.data.shipments.length()").value(1))
                .andExpect(jsonPath("$.data.shipments[0].trackingNo").value("SF123456789"));

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

    private long findUserIdByHrCode(JsonNode users, String hrCode) {
        if (users == null || !users.isArray()) {
            throw new IllegalStateException("用户列表返回格式错误");
        }
        for (JsonNode user : users) {
            if (hrCode.equals(user.path("hrCode").asText())) {
                return user.path("id").asLong();
            }
        }
        throw new IllegalStateException("未找到人力资源码对应用户：" + hrCode);
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
