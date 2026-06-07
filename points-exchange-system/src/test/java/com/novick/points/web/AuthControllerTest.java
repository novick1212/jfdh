package com.novick.points.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:points-auth-test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.sms-mock-enabled=true",
        "app.csrf-enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLoginBySmsCode() throws Exception {
        // SMS 功能已关闭，使用人力资源码+姓名直接登录
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/sms-login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"演示用户\",\"hrCode\":\"HR0001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("demo"));
    }

    @Test
    void shouldRejectLoginWithWrongDisplayName() throws Exception {
        mockMvc.perform(post("/api/auth/sms-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"错误姓名\",\"hrCode\":\"HR0001\"}"))
                .andExpect(status().isBadRequest());
    }
}
