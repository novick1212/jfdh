package com.novick.points.web.app;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novick.points.domain.ExchangeOrder;
import com.novick.points.domain.OrderStatus;
import com.novick.points.domain.UserAccount;
import com.novick.points.domain.UserRole;
import com.novick.points.repository.ExchangeOrderRepository;
import com.novick.points.repository.UserAccountRepository;
import com.novick.points.security.SessionAuthService;
import com.novick.points.security.SessionPrincipal;
import com.novick.points.service.Kuaidi100Client;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:points-tracking-test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "KUAIDI100_CUSTOMER=test-customer",
        "KUAIDI100_KEY=test-key"
})
@AutoConfigureMockMvc
@Transactional
class OrderTrackingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ExchangeOrderRepository exchangeOrderRepository;

    @MockBean
    private Kuaidi100Client kuaidi100Client;

    @Test
    void shouldGetTrackingFromMockedClient() throws Exception {
        UserAccount demoUser = userAccountRepository.findByUsername("demo").orElseThrow();

        JsonNode mock = objectMapper.readTree(
                "{\"status\":\"200\",\"state\":\"0\",\"com\":\"yuantong\",\"nu\":\"YT123456789\",\"data\":[{\"ftime\":\"2026-06-03 10:00:00\",\"context\":\"您的快件已揽收\"}]}");
        when(kuaidi100Client.query(anyString(), anyString(), anyString())).thenReturn(mock);

        ExchangeOrder order = new ExchangeOrder();
        order.setOrderNo("JFTRACK0001");
        order.setUserId(demoUser.getId());
        order.setItemId(1L);
        order.setItemName("测试商品");
        order.setQuantity(1);
        order.setPointsCost(0);
        order.setTotalPoints(0);
        order.setStatus(OrderStatus.FULFILLED);
        order.setRecipientName("张三");
        order.setPhone("13800000000");
        order.setAddress("测试地址");
        order.setShippingCarrier("yuantong");
        order.setTrackingNo("YT123456789");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        exchangeOrderRepository.save(order);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuthService.SESSION_KEY,
                new SessionPrincipal(demoUser.getId(), demoUser.getUsername(), demoUser.getDisplayName(), UserRole.USER));

        MvcResult result = mockMvc.perform(get("/api/app/orders/" + order.getId() + "/tracking")
                        .session(session)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(root.path("success").asBoolean(), is(true));
        assertThat(root.path("data").path("state").asText(), is("0"));
        assertThat(root.path("data").path("data").get(0).path("context").asText(), containsString("已揽收"));
    }
}
