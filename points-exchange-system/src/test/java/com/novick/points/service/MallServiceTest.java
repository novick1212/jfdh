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
import com.novick.points.domain.OrderStatus;
import com.novick.points.repository.ExchangeOrderRepository;
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

    @Autowired
    private ExchangeOrderRepository exchangeOrderRepository;

    @Test
    void shouldCreateExchangeOrderAndConsumeQuota() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId();

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemId);
        command.setQuantity(1);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);

        assertEquals(1, userAccountRepository.findById(userId).orElseThrow().getRedeemUsed());
    }

    @Test
    void shouldRejectOrderWhenUserHasRedeemed() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        List<Long> itemIds = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().stream()
                .limit(2)
                .map(it -> it.getId())
                .collect(java.util.stream.Collectors.toList());

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemIds.get(0));
        command.setQuantity(1);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");
        mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);

        MallService.CreateOrderCommand second = new MallService.CreateOrderCommand();
        second.setItemId(itemIds.get(1));
        second.setQuantity(1);
        second.setRecipientName("测试用户");
        second.setPhone("13800000000");
        second.setAddress("成都市高新区");

        assertThrows(BusinessException.class,
                () -> mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), second));
    }

    @Test
    void shouldCheckoutSingleItemAndConsumeQuota() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId();

        MallService.CheckoutItemCommand i1 = new MallService.CheckoutItemCommand();
        i1.setItemId(itemId);
        i1.setQuantity(1);

        MallService.CheckoutCommand command = new MallService.CheckoutCommand();
        command.setItems(List.of(i1));
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        List<Map<String, Object>> orders = mallService.checkout(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);
        assertEquals(1, orders.size());
        assertEquals(1, userAccountRepository.findById(userId).orElseThrow().getRedeemUsed());
    }

    @Test
    void shouldRejectCheckoutWhenSelectingMultipleItems() {
        MallService.CheckoutItemCommand i1 = new MallService.CheckoutItemCommand();
        i1.setItemId(rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId());
        i1.setQuantity(1);
        MallService.CheckoutItemCommand i2 = new MallService.CheckoutItemCommand();
        i2.setItemId(rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(1).getId());
        i2.setQuantity(1);

        MallService.CheckoutCommand command = new MallService.CheckoutCommand();
        command.setItems(List.of(i1, i2));
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");

        assertThrows(BusinessException.class,
                () -> mallService.checkout(new SessionPrincipal(
                        userAccountRepository.findByUsername("demo").orElseThrow().getId(),
                        "demo", "演示用户", UserRole.USER), command));
    }

    @Test
    void shouldNotDeleteItemWhenOrderExists() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId();

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemId);
        command.setQuantity(1);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");
        mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);

        assertThrows(BusinessException.class, () -> mallService.deleteItem(itemId));
    }

    @Test
    void shouldFulfillOrderWithTrackingInfo() {
        Long userId = userAccountRepository.findByUsername("demo").orElseThrow().getId();
        Long itemId = rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().get(0).getId();

        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(itemId);
        command.setQuantity(1);
        command.setRecipientName("测试用户");
        command.setPhone("13800000000");
        command.setAddress("成都市高新区");
        Map<String, Object> created = mallService.createOrder(new SessionPrincipal(userId, "demo", "演示用户", UserRole.USER), command);

        Long orderId = ((Number) created.get("id")).longValue();
        Map<String, Object> fulfilled = mallService.fulfillOrder(orderId, "yuantong", "YT10001");

        assertEquals("yuantong", fulfilled.get("shippingCarrier"));
        assertEquals("YT10001", fulfilled.get("trackingNo"));
        assertEquals(OrderStatus.FULFILLED, exchangeOrderRepository.findById(orderId).orElseThrow().getStatus());
    }
}
