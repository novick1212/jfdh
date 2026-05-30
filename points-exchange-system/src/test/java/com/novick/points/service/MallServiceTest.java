package com.novick.points.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.novick.points.common.BusinessException;
import com.novick.points.domain.UserRole;
import com.novick.points.repository.RewardItemRepository;
import com.novick.points.repository.UserAccountRepository;
import com.novick.points.security.SessionPrincipal;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:points-test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class MallServiceTest {

    @Autowired
    private MallService mallService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RewardItemRepository rewardItemRepository;

    @Test
    void shouldCreateExchangeOrderAndDeductPoints() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId();

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemId);
        command.setQuantity(1);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);

        assertEquals(901, userAccountRepository.findById(userId).orElseThrow().getPointsBalance());
    }

    @Test
    void shouldRejectOrderWhenPointsInsufficient() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(2).getId();

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemId);
        command.setQuantity(2);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        assertThrows(BusinessException.class,
                () -> mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command));
    }
}
