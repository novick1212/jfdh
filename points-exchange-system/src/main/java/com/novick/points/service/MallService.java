package com.novick.points.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.novick.points.common.BusinessException;
import com.novick.points.domain.ExchangeOrder;
import com.novick.points.domain.OrderStatus;
import com.novick.points.domain.PointsTransaction;
import com.novick.points.domain.RewardItem;
import com.novick.points.domain.TransactionType;
import com.novick.points.domain.UserAccount;
import com.novick.points.repository.ExchangeOrderRepository;
import com.novick.points.repository.PointsTransactionRepository;
import com.novick.points.repository.RewardItemRepository;
import com.novick.points.repository.UserAccountRepository;
import com.novick.points.security.SessionPrincipal;

@Service
public class MallService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RewardItemRepository rewardItemRepository;
    private final ExchangeOrderRepository exchangeOrderRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final UserAccountRepository userAccountRepository;

    public MallService(RewardItemRepository rewardItemRepository, ExchangeOrderRepository exchangeOrderRepository,
            PointsTransactionRepository pointsTransactionRepository, UserAccountRepository userAccountRepository) {
        this.rewardItemRepository = rewardItemRepository;
        this.exchangeOrderRepository = exchangeOrderRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public Map<String, Object> appHome(SessionPrincipal principal) {
        UserAccount user = getUser(principal.getUserId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", toUserMap(user));
        result.put("items", rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().stream()
                .map(this::toItemMap)
                .collect(Collectors.toList()));
        result.put("recentOrders", exchangeOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .limit(5)
                .map(this::toOrderMap)
                .collect(Collectors.toList()));
        result.put("transactions", pointsTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .limit(10)
                .map(this::toTransactionMap)
                .collect(Collectors.toList()));
        return result;
    }

    public List<Map<String, Object>> listActiveItems() {
        return rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().stream()
                .map(this::toItemMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listUserOrders(Long userId) {
        return exchangeOrderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toOrderMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listUserTransactions(Long userId) {
        return pointsTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toTransactionMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createOrder(SessionPrincipal principal, CreateOrderCommand command) {
        UserAccount user = getUser(principal.getUserId());
        RewardItem item = rewardItemRepository.findById(command.getItemId())
                .orElseThrow(() -> new BusinessException("兑换商品不存在"));
        if (!item.isActive()) {
            throw new BusinessException("商品已下架");
        }
        if (command.getQuantity() == null || command.getQuantity() < 1) {
            throw new BusinessException("兑换数量必须大于 0");
        }
        if (item.getStock() < command.getQuantity()) {
            throw new BusinessException("库存不足");
        }
        int totalPoints = item.getPointsCost() * command.getQuantity();
        if (user.getPointsBalance() < totalPoints) {
            throw new BusinessException("积分不足");
        }

        user.setPointsBalance(user.getPointsBalance() - totalPoints);
        item.setStock(item.getStock() - command.getQuantity());

        ExchangeOrder order = new ExchangeOrder();
        order.setOrderNo(generateOrderNo(user.getId()));
        order.setUserId(user.getId());
        order.setItemId(item.getId());
        order.setItemName(item.getName());
        order.setQuantity(command.getQuantity());
        order.setPointsCost(item.getPointsCost());
        order.setTotalPoints(totalPoints);
        order.setStatus(OrderStatus.CREATED);
        order.setRecipientName(command.getRecipientName());
        order.setPhone(command.getPhone());
        order.setAddress(command.getAddress());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        userAccountRepository.save(user);
        rewardItemRepository.save(item);
        exchangeOrderRepository.save(order);
        recordTransaction(user, TransactionType.REDEEM, -totalPoints, "订单兑换", "兑换商品 " + item.getName());
        return toOrderMap(order);
    }

    public Map<String, Object> adminSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userAccountRepository.count());
        data.put("itemCount", rewardItemRepository.count());
        data.put("orderCount", exchangeOrderRepository.count());
        data.put("recentTransactions", pointsTransactionRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toTransactionMap)
                .collect(Collectors.toList()));
        return data;
    }

    public List<Map<String, Object>> listAllItems() {
        return rewardItemRepository.findAll().stream()
                .sorted((left, right) -> right.getId().compareTo(left.getId()))
                .map(this::toItemMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> saveItem(ItemCommand command) {
        RewardItem item = command.getId() == null ? new RewardItem()
                : rewardItemRepository.findById(command.getId()).orElseThrow(() -> new BusinessException("商品不存在"));
        item.setName(command.getName());
        item.setDescription(command.getDescription());
        item.setPointsCost(command.getPointsCost());
        item.setStock(command.getStock());
        item.setCoverImage(command.getCoverImage());
        item.setActive(command.isActive());
        item.setSortOrder(command.getSortOrder() == null ? 0 : command.getSortOrder());
        rewardItemRepository.save(item);
        return toItemMap(item);
    }

    public List<Map<String, Object>> listAllOrders() {
        return exchangeOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toOrderMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> fulfillOrder(Long orderId) {
        ExchangeOrder order = exchangeOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException("当前订单不可发货");
        }
        order.setStatus(OrderStatus.FULFILLED);
        order.setUpdatedAt(LocalDateTime.now());
        exchangeOrderRepository.save(order);
        return toOrderMap(order);
    }

    public List<Map<String, Object>> listUsers() {
        return userAccountRepository.findAll().stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId()))
                .map(this::toUserMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> adjustPoints(Long userId, Integer delta, String note) {
        if (delta == null || delta == 0) {
            throw new BusinessException("调整积分不能为 0");
        }
        UserAccount user = getUser(userId);
        int nextBalance = user.getPointsBalance() + delta;
        if (nextBalance < 0) {
            throw new BusinessException("调整后积分不能小于 0");
        }
        user.setPointsBalance(nextBalance);
        userAccountRepository.save(user);
        recordTransaction(user, TransactionType.ADJUST, delta, "后台调整", note == null ? "后台调整积分" : note);
        return toUserMap(user);
    }

    private UserAccount getUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private void recordTransaction(UserAccount user, TransactionType type, Integer amount, String source, String note) {
        PointsTransaction transaction = new PointsTransaction();
        transaction.setUserId(user.getId());
        transaction.setType(type);
        transaction.setChangeAmount(amount);
        transaction.setBalanceAfter(user.getPointsBalance());
        transaction.setSource(source);
        transaction.setNote(note);
        transaction.setCreatedAt(LocalDateTime.now());
        pointsTransactionRepository.save(transaction);
    }

    private String generateOrderNo(Long userId) {
        long suffix = Math.abs(System.nanoTime() % 10000);
        return "JF" + LocalDateTime.now().format(ORDER_NO_FORMATTER) + String.format("%04d%04d", userId % 10000, suffix);
    }

    private Map<String, Object> toItemMap(RewardItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getId());
        data.put("name", item.getName());
        data.put("description", item.getDescription());
        data.put("pointsCost", item.getPointsCost());
        data.put("stock", item.getStock());
        data.put("coverImage", item.getCoverImage());
        data.put("active", item.isActive());
        data.put("sortOrder", item.getSortOrder());
        return data;
    }

    private Map<String, Object> toOrderMap(ExchangeOrder order) {
        UserAccount user = userAccountRepository.findById(order.getUserId()).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("userId", order.getUserId());
        data.put("userName", user == null ? "-" : user.getDisplayName());
        data.put("itemName", order.getItemName());
        data.put("quantity", order.getQuantity());
        data.put("totalPoints", order.getTotalPoints());
        data.put("status", order.getStatus());
        data.put("recipientName", order.getRecipientName());
        data.put("phone", order.getPhone());
        data.put("address", order.getAddress());
        data.put("createdAt", order.getCreatedAt());
        return data;
    }

    private Map<String, Object> toTransactionMap(PointsTransaction transaction) {
        UserAccount user = userAccountRepository.findById(transaction.getUserId()).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", transaction.getId());
        data.put("userId", transaction.getUserId());
        data.put("userName", user == null ? "-" : user.getDisplayName());
        data.put("type", transaction.getType());
        data.put("changeAmount", transaction.getChangeAmount());
        data.put("balanceAfter", transaction.getBalanceAfter());
        data.put("source", transaction.getSource());
        data.put("note", transaction.getNote());
        data.put("createdAt", transaction.getCreatedAt());
        return data;
    }

    private Map<String, Object> toUserMap(UserAccount user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        data.put("role", user.getRole());
        data.put("pointsBalance", user.getPointsBalance());
        data.put("enabled", user.isEnabled());
        return data;
    }

    public static class CreateOrderCommand {
        private Long itemId;
        private Integer quantity;
        private String recipientName;
        private String phone;
        private String address;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getRecipientName() {
            return recipientName;
        }

        public void setRecipientName(String recipientName) {
            this.recipientName = recipientName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    public static class ItemCommand {
        private Long id;
        private String name;
        private String description;
        private Integer pointsCost;
        private Integer stock;
        private String coverImage;
        private boolean active;
        private Integer sortOrder;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getPointsCost() {
            return pointsCost;
        }

        public void setPointsCost(Integer pointsCost) {
            this.pointsCost = pointsCost;
        }

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }

        public String getCoverImage() {
            return coverImage;
        }

        public void setCoverImage(String coverImage) {
            this.coverImage = coverImage;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
