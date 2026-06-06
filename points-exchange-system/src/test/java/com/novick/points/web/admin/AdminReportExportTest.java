package com.novick.points.web.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.novick.points.domain.ExchangeOrder;
import com.novick.points.domain.OrderStatus;
import com.novick.points.domain.UserAccount;
import com.novick.points.domain.UserRole;
import com.novick.points.repository.ExchangeOrderRepository;
import com.novick.points.repository.UserAccountRepository;
import com.novick.points.security.SessionAuthService;
import com.novick.points.security.SessionPrincipal;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:points-export-test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class AdminReportExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ExchangeOrderRepository exchangeOrderRepository;

    @Test
    void shouldExportRedemptionReportXlsx() throws Exception {
        UserAccount demoUser = userAccountRepository.findByUsername("demo").orElseThrow();

        UserAccount u2 = new UserAccount();
        u2.setUsername("u2");
        u2.setPasswordHash("x");
        u2.setDisplayName("未兑换用户");
        u2.setPhoneNumber("13900000000");
        u2.setHrCode("HR0002");
        u2.setRole(UserRole.USER);
        u2.setEnabled(true);
        u2.setPointsBalance(0);
        u2.setRedeemQuota(2);
        userAccountRepository.save(u2);

        ExchangeOrder order = new ExchangeOrder();
        order.setOrderNo("JFTEST0001");
        order.setUserId(demoUser.getId());
        order.setItemId(1L);
        order.setItemName("测试商品");
        order.setQuantity(1);
        order.setPointsCost(0);
        order.setTotalPoints(0);
        order.setStatus(OrderStatus.CREATED);
        order.setRecipientName("张三");
        order.setPhone("13800000000");
        order.setAddress("测试地址");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        exchangeOrderRepository.save(order);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuthService.SESSION_KEY,
                new SessionPrincipal(1L, "admin", "系统管理员", UserRole.ADMIN));

        MvcResult result = mockMvc.perform(get("/api/admin/reports/redemption").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes.length, greaterThan(100));
        assertThat(new String(bytes, 0, 2), containsString("PK"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet redeemed = workbook.getSheet("已兑换明细");
            Sheet notRedeemed = workbook.getSheet("未兑换名单");
            assertThat(redeemed != null, is(true));
            assertThat(notRedeemed != null, is(true));

            Row redeemedRow = redeemed.getRow(1);
            assertThat(redeemedRow.getCell(1).getStringCellValue(), is("演示用户"));
            assertThat(redeemedRow.getCell(4).getStringCellValue(), is("测试商品"));
            assertThat(redeemedRow.getCell(5).getStringCellValue(), is("CREATED"));

            Row notRedeemedRow = notRedeemed.getRow(1);
            assertThat(notRedeemedRow.getCell(1).getStringCellValue(), is("未兑换用户"));
        }
    }
}
