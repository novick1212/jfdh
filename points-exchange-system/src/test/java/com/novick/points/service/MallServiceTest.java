package com.novick.points.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

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
    void shouldCreateExchangeOrderAndConsumeQuota() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId();

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemId);
        command.setQuantity(2);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);

        assertEquals(2, userAccountRepository.findById(userId).orElseThrow().getRedeemUsed());
    }

    @Test
    void shouldRejectOrderWhenQuotaInsufficient() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(2).getId();

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemId);
        command.setQuantity(4);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        assertThrows(BusinessException.class,
                () -> mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command));
    }

    @Test
    void shouldCheckoutCartAndConsumeQuota() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        List<Long> itemIds = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().stream()
                .limit(2)
                .map(it -> it.getId())
                .collect(java.util.stream.Collectors.toList());

        MallService.CheckoutItemCommand i1 = new MallService.CheckoutItemCommand();
        i1.setItemId(itemIds.get(0));
        i1.setQuantity(1);
        MallService.CheckoutItemCommand i2 = new MallService.CheckoutItemCommand();
        i2.setItemId(itemIds.get(1));
        i2.setQuantity(2);

        MallService.CheckoutCommand command = new MallService.CheckoutCommand();
        command.setItems(List.of(i1, i2));
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        List<Map<String, Object>> orders = mallService.checkout(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);
        assertEquals(2, orders.size());
        assertEquals(3, userAccountRepository.findById(userId).orElseThrow().getRedeemUsed());
    }

    @Test
    void shouldRejectCheckoutWhenQuotaInsufficient() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId();

        MallService.CheckoutItemCommand i1 = new MallService.CheckoutItemCommand();
        i1.setItemId(itemId);
        i1.setQuantity(4);

        MallService.CheckoutCommand command = new MallService.CheckoutCommand();
        command.setItems(List.of(i1));
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        assertThrows(BusinessException.class,
                () -> mallService.checkout(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command));
    }
}
