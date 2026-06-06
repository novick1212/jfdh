package com.novick.points.service;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novick.points.common.BusinessException;
import com.novick.points.domain.ExchangeOrder;
import com.novick.points.domain.OrderStatus;
import com.novick.points.domain.PointsTransaction;
import com.novick.points.domain.RewardItem;
import com.novick.points.domain.SiteConfig;
import com.novick.points.domain.TransactionType;
import com.novick.points.domain.UserAccount;
import com.novick.points.domain.UserRole;
import com.novick.points.repository.ExchangeOrderRepository;
import com.novick.points.repository.PointsTransactionRepository;
import com.novick.points.repository.RewardItemRepository;
import com.novick.points.repository.SiteConfigRepository;
import com.novick.points.repository.UserAccountRepository;
import com.novick.points.security.SessionPrincipal;

@Service
public class MallService {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int USER_REDEEM_LIMIT = 1;

    private final RewardItemRepository rewardItemRepository;
    private final ExchangeOrderRepository exchangeOrderRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final UserAccountRepository userAccountRepository;
    private final SiteConfigRepository siteConfigRepository;
    private final AuthService authService;
    private final Kuaidi100Client kuaidi100Client;
    private final ObjectMapper objectMapper;

    public MallService(RewardItemRepository rewardItemRepository, ExchangeOrderRepository exchangeOrderRepository,
            PointsTransactionRepository pointsTransactionRepository, UserAccountRepository userAccountRepository,
            SiteConfigRepository siteConfigRepository, AuthService authService, Kuaidi100Client kuaidi100Client,
            ObjectMapper objectMapper) {
        this.rewardItemRepository = rewardItemRepository;
        this.exchangeOrderRepository = exchangeOrderRepository;
        this.pointsTransactionRepository = pointsTransactionRepository;
        this.userAccountRepository = userAccountRepository;
        this.siteConfigRepository = siteConfigRepository;
        this.authService = authService;
        this.kuaidi100Client = kuaidi100Client;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> appHome(SessionPrincipal principal) {
        UserAccount user = getUser(principal.getUserId());
        List<Map<String, Object>> items = listActiveItems();
        List<Map<String, Object>> orders = listUserOrders(user.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", toUserMap(user));
        result.put("siteConfig", toSiteConfigMap(getOrCreateSiteConfig()));
        result.put("items", items);
        result.put("recentOrders", orders);
        result.put("latestOrder", orders.isEmpty() ? null : orders.get(0));
        result.put("canRedeem", !hasUserRedeemed(user));
        return result;
    }

    public Map<String, Object> getSiteConfig() {
        return toSiteConfigMap(getOrCreateSiteConfig());
    }

    @Transactional
    public Map<String, Object> saveSiteConfig(SiteConfigCommand command) {
        if (command == null) {
            throw new BusinessException("配置不能为空");
        }
        SiteConfig config = getOrCreateSiteConfig();
        config.setAnnouncementHtml(sanitizeAnnouncementHtml(command.getAnnouncementHtml()));
        config.setHeroImageUrl(requireText(command.getHeroImageUrl(), "请填写活动图片地址"));
        config.setHotline(requireText(command.getHotline(), "请填写客服热线"));
        siteConfigRepository.save(config);
        return toSiteConfigMap(config);
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

    @Transactional
    public Map<String, Object> updateContactInfo(SessionPrincipal principal, ContactCommand command) {
        UserAccount user = getUser(principal.getUserId());
        String contactName = requireText(command.getContactName(), "请输入收货人");
        String contactPhone = requirePhone(command.getContactPhone(), "请输入正确的手机号");
        String contactAddress = requireText(command.getContactAddress(), "请输入收货地址");
        user.setContactName(contactName);
        user.setContactPhone(contactPhone);
        user.setContactAddress(contactAddress);
        userAccountRepository.save(user);
        return toUserMap(user);
    }

    @Transactional
    public Map<String, Object> getOrderTracking(SessionPrincipal principal, Long orderId) {
        if (orderId == null) {
            throw new BusinessException("订单不存在");
        }
        ExchangeOrder order = exchangeOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (principal == null || principal.getUserId() == null || !principal.getUserId().equals(order.getUserId())) {
            throw new BusinessException("无权限访问");
        }
        if (order.getTrackingNo() == null || order.getTrackingNo().trim().isEmpty()) {
            throw new BusinessException("暂无物流信息");
        }
        if (order.getShippingCarrier() == null || order.getShippingCarrier().trim().isEmpty()) {
            throw new BusinessException("暂无快递公司编码（快递100 com），请联系管理员补充");
        }

        int minIntervalSeconds = 1800;
        if (order.getTrackingUpdatedAt() != null && order.getTrackingData() != null && !order.getTrackingData().trim().isEmpty()) {
            long seconds = Duration.between(order.getTrackingUpdatedAt(), LocalDateTime.now()).getSeconds();
            if (seconds >= 0 && seconds < minIntervalSeconds) {
                try {
                    Map<String, Object> cached = objectMapper.readValue(order.getTrackingData(), Map.class);
                    cached.put("cachedAt", order.getTrackingUpdatedAt().format(EXPORT_TIME_FORMATTER));
                    return cached;
                } catch (Exception e) {
                    order.setTrackingData(null);
                }
            }
        }

        JsonNode root = kuaidi100Client.query(order.getShippingCarrier(), order.getTrackingNo(), order.getPhone());
        try {
            order.setTrackingData(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            order.setTrackingData(root.toString());
        }
        order.setTrackingState(root.path("state").asText(null));
        order.setTrackingUpdatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        exchangeOrderRepository.save(order);

        Map<String, Object> result = objectMapper.convertValue(root, Map.class);
        result.put("cachedAt", order.getTrackingUpdatedAt().format(EXPORT_TIME_FORMATTER));
        return result;
    }

    public List<Map<String, Object>> listUserTransactions(Long userId) {
        return pointsTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toTransactionMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createOrder(SessionPrincipal principal, CreateOrderCommand command) {
        if (command == null) {
            throw new BusinessException("请选择兑换方案");
        }
        UserAccount user = getUser(principal.getUserId());
        assertCanRedeemOnce(user);

        RewardItem item = rewardItemRepository.findById(command.getItemId())
                .orElseThrow(() -> new BusinessException("兑换方案不存在"));
        if (!item.isActive()) {
            throw new BusinessException("方案已下架");
        }
        if (item.getStock() == null || item.getStock() < 1) {
            throw new BusinessException("方案库存不足");
        }
        if (command.getQuantity() != null && command.getQuantity() != 1) {
            throw new BusinessException("每次只能选择 1 个方案");
        }

        String recipientName = requireText(command.getRecipientName(), "请输入收货人");
        String phone = requirePhone(command.getPhone(), "请输入正确的手机号");
        String address = requireText(command.getAddress(), "请输入收货地址");

        user.setContactName(recipientName);
        user.setContactPhone(phone);
        user.setContactAddress(address);
        user.setRedeemQuota(USER_REDEEM_LIMIT);
        user.setRedeemUsed(USER_REDEEM_LIMIT);
        userAccountRepository.save(user);

        item.setStock(item.getStock() - 1);
        rewardItemRepository.save(item);

        ExchangeOrder order = new ExchangeOrder();
        order.setOrderNo(generateOrderNo(user.getId()));
        order.setUserId(user.getId());
        order.setItemId(item.getId());
        order.setItemName(item.getName());
        order.setQuantity(1);
        order.setPointsCost(0);
        order.setTotalPoints(0);
        order.setStatus(OrderStatus.CREATED);
        order.setRecipientName(recipientName);
        order.setPhone(phone);
        order.setAddress(address);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        exchangeOrderRepository.save(order);
        return toOrderMap(order);
    }

    @Transactional
    public List<Map<String, Object>> checkout(SessionPrincipal principal, CheckoutCommand command) {
        if (command == null || command.getItems() == null || command.getItems().isEmpty()) {
            throw new BusinessException("请选择兑换方案");
        }
        if (command.getItems().size() > 1) {
            throw new BusinessException("每位用户仅可七选一，只能提交 1 个方案");
        }
        CheckoutItemCommand item = command.getItems().get(0);
        CreateOrderCommand create = new CreateOrderCommand();
        create.setItemId(item == null ? null : item.getItemId());
        create.setQuantity(item == null ? null : item.getQuantity());
        create.setRecipientName(command.getRecipientName());
        create.setPhone(command.getPhone());
        create.setAddress(command.getAddress());
        return List.of(createOrder(principal, create));
    }

    public Map<String, Object> adminSummary() {
        Map<String, Object> data = new LinkedHashMap<>();
        long userCount = userAccountRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.USER)
                .count();
        data.put("userCount", userCount);
        data.put("itemCount", rewardItemRepository.count());
        data.put("activeItemCount", rewardItemRepository.findByActiveTrueOrderBySortOrderAscIdDesc().size());
        data.put("orderCount", exchangeOrderRepository.count());
        data.put("recentTransactions", pointsTransactionRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toTransactionMap)
                .collect(Collectors.toList()));
        return data;
    }

    public List<Map<String, Object>> listAllItems() {
        return rewardItemRepository.findAll().stream()
                .sorted(Comparator.comparing(RewardItem::getSortOrder).thenComparing(RewardItem::getId).reversed())
                .map(this::toItemMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> saveItem(ItemCommand command) {
        RewardItem item = command.getId() == null ? new RewardItem()
                : rewardItemRepository.findById(command.getId()).orElseThrow(() -> new BusinessException("方案不存在"));
        item.setName(requireText(command.getName(), "请输入方案名称"));
        item.setDescription(requireText(command.getDescription(), "请输入方案说明"));
        item.setPointsCost(0);
        item.setStock(command.getStock() == null ? 0 : Math.max(0, command.getStock()));
        item.setCoverImage(requireText(command.getCoverImage(), "请输入方案图片地址"));
        item.setActive(command.isActive());
        item.setSortOrder(command.getSortOrder() == null ? 0 : command.getSortOrder());
        rewardItemRepository.save(item);
        return toItemMap(item);
    }

    @Transactional
    public void deleteItem(Long itemId) {
        if (itemId == null) {
            throw new BusinessException("方案不存在");
        }
        RewardItem item = rewardItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException("方案不存在"));
        if (exchangeOrderRepository.existsByItemId(itemId)) {
            throw new BusinessException("该方案已有订单，不能删除，请改为下架");
        }
        rewardItemRepository.delete(item);
    }

    public List<Map<String, Object>> listAllOrders() {
        return exchangeOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toOrderMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> fulfillOrder(Long orderId, String shippingCarrier, String trackingNo) {
        ExchangeOrder order = exchangeOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException("当前订单不可发货");
        }
        if (trackingNo != null && !trackingNo.isBlank()) {
            order.setTrackingNo(trackingNo.trim());
            order.setTrackingUrl("https://m.kuaidi100.com/result.jsp?nu=" + trackingNo.trim());
        } else {
            order.setTrackingNo(null);
            order.setTrackingUrl(null);
        }
        if (shippingCarrier != null && !shippingCarrier.isBlank()) {
            order.setShippingCarrier(shippingCarrier.trim().toLowerCase());
        } else {
            order.setShippingCarrier(null);
        }
        order.setTrackingState(null);
        order.setTrackingData(null);
        order.setTrackingUpdatedAt(null);
        order.setStatus(OrderStatus.FULFILLED);
        order.setFulfilledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        exchangeOrderRepository.save(order);
        return toOrderMap(order);
    }

    public List<Map<String, Object>> listUsers() {
        return userAccountRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.USER)
                .sorted(Comparator.comparing(UserAccount::getId))
                .map(this::toUserMap)
                .collect(Collectors.toList());
    }

    public byte[] exportRedemptionReportXlsx() {
        List<UserAccount> whitelistUsers = userAccountRepository.findAll().stream()
                .filter(this::isWhitelistUser)
                .sorted(Comparator.comparing(UserAccount::getId))
                .collect(Collectors.toList());

        Map<Long, UserAccount> userMap = new HashMap<>();
        for (UserAccount user : whitelistUsers) {
            userMap.put(user.getId(), user);
        }

        List<ExchangeOrder> orders = exchangeOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(order -> userMap.containsKey(order.getUserId()))
                .collect(Collectors.toList());

        Set<Long> redeemedUserIds = orders.stream()
                .map(ExchangeOrder::getUserId)
                .collect(Collectors.toSet());

        List<UserAccount> notRedeemedUsers = whitelistUsers.stream()
                .filter(user -> !redeemedUserIds.contains(user.getId()))
                .collect(Collectors.toList());

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeRedeemedSheet(workbook, orders, userMap);
            writeNotRedeemedSheet(workbook, notRedeemedUsers);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("导出失败：" + e.getMessage());
        }
    }

    private void writeRedeemedSheet(XSSFWorkbook workbook, List<ExchangeOrder> orders, Map<Long, UserAccount> userMap) {
        Sheet sheet = workbook.createSheet("已兑换明细");
        int rowIndex = 0;

        Row header = sheet.createRow(rowIndex++);
        writeCell(header, 0, "用户ID");
        writeCell(header, 1, "姓名");
        writeCell(header, 2, "手机号");
        writeCell(header, 3, "人力资源码");
        writeCell(header, 4, "兑换方案");
        writeCell(header, 5, "订单状态");
        writeCell(header, 6, "收货人");
        writeCell(header, 7, "收货手机号");
        writeCell(header, 8, "收货地址");
        writeCell(header, 9, "下单时间");

        for (ExchangeOrder order : orders) {
            UserAccount user = userMap.get(order.getUserId());
            Row row = sheet.createRow(rowIndex++);
            writeNumber(row, 0, order.getUserId());
            writeCell(row, 1, user == null ? "" : safe(user.getDisplayName()));
            writeCell(row, 2, user == null ? "" : safe(user.getPhoneNumber()));
            writeCell(row, 3, user == null ? "" : safe(user.getHrCode()));
            writeCell(row, 4, safe(order.getItemName()));
            writeCell(row, 5, order.getStatus() == null ? "" : order.getStatus().name());
            writeCell(row, 6, safe(order.getRecipientName()));
            writeCell(row, 7, safe(order.getPhone()));
            writeCell(row, 8, safe(order.getAddress()));
            writeCell(row, 9, order.getCreatedAt() == null ? "" : order.getCreatedAt().format(EXPORT_TIME_FORMATTER));
        }
    }

    private void writeNotRedeemedSheet(XSSFWorkbook workbook, List<UserAccount> users) {
        Sheet sheet = workbook.createSheet("未兑换名单");
        int rowIndex = 0;

        Row header = sheet.createRow(rowIndex++);
        writeCell(header, 0, "用户ID");
        writeCell(header, 1, "姓名");
        writeCell(header, 2, "手机号");
        writeCell(header, 3, "人力资源码");
        writeCell(header, 4, "默认收货人");
        writeCell(header, 5, "默认收货手机号");
        writeCell(header, 6, "默认收货地址");
        writeCell(header, 7, "启用");

        for (UserAccount user : users) {
            Row row = sheet.createRow(rowIndex++);
            writeNumber(row, 0, user.getId());
            writeCell(row, 1, safe(user.getDisplayName()));
            writeCell(row, 2, safe(user.getPhoneNumber()));
            writeCell(row, 3, safe(user.getHrCode()));
            writeCell(row, 4, safe(user.getContactName()));
            writeCell(row, 5, safe(user.getContactPhone()));
            writeCell(row, 6, safe(user.getContactAddress()));
            writeCell(row, 7, user.isEnabled() ? "是" : "否");
        }
    }

    private boolean isWhitelistUser(UserAccount user) {
        if (user == null || user.getRole() != UserRole.USER) {
            return false;
        }
        return !(safe(user.getPhoneNumber()).isBlank()
                || safe(user.getHrCode()).isBlank()
                || safe(user.getDisplayName()).isBlank());
    }

    private void writeCell(Row row, int colIndex, String value) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value == null ? "" : value);
    }

    private void writeNumber(Row row, int colIndex, Number value) {
        Cell cell = row.createCell(colIndex);
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        cell.setCellValue(value.doubleValue());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Transactional
    public Map<String, Object> setRedeemQuota(Long userId, Integer quota) {
        if (quota == null || quota < 0 || quota > USER_REDEEM_LIMIT) {
            throw new BusinessException("当前系统仅支持设置为 0 或 1 次");
        }
        UserAccount user = getUser(userId);
        if ((user.getRedeemUsed() == null ? 0 : user.getRedeemUsed()) > quota) {
            throw new BusinessException("兑换次数不能小于已用次数");
        }
        user.setRedeemQuota(quota);
        userAccountRepository.save(user);
        return toUserMap(user);
    }

    @Transactional
    public Map<String, Object> importUsers(List<UserImportCommand> users) {
        if (users == null || users.isEmpty()) {
            throw new BusinessException("导入数据为空");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        int created = 0;
        int updated = 0;

        for (UserImportCommand command : users) {
            if (command == null) {
                continue;
            }
            String phone = requirePhone(command.getPhoneNumber(), "手机号格式不正确", false);
            if (phone == null) {
                errors.add(errorMap(command.getLineNo(), command.getRaw(), "手机号格式不正确"));
                continue;
            }
            String displayName = normalize(command.getDisplayName());
            if (displayName == null) {
                errors.add(errorMap(command.getLineNo(), command.getRaw(), "姓名不能为空"));
                continue;
            }
            String hrCode = normalize(command.getHrCode());
            if (hrCode == null) {
                errors.add(errorMap(command.getLineNo(), command.getRaw(), "人力资源码不能为空"));
                continue;
            }

            UserAccount user = userAccountRepository.findByPhoneNumber(phone).orElse(null);
            UserAccount byHrCode = userAccountRepository.findByHrCode(hrCode).orElse(null);
            if (byHrCode != null && (user == null || !byHrCode.getId().equals(user.getId()))) {
                errors.add(errorMap(command.getLineNo(), command.getRaw(), "人力资源码已被其它用户占用"));
                continue;
            }

            if (user == null) {
                String username = normalize(command.getUsername());
                if (username == null) {
                    username = phone;
                }
                UserAccount byUsername = userAccountRepository.findByUsername(username).orElse(null);
                if (byUsername != null) {
                    errors.add(errorMap(command.getLineNo(), command.getRaw(), "用户名已被其它手机号占用"));
                    continue;
                }
                user = new UserAccount();
                user.setUsername(username);
                user.setPhoneNumber(phone);
                user.setDisplayName(displayName);
                user.setHrCode(hrCode);
                user.setRole(UserRole.USER);
                user.setEnabled(true);
                user.setPointsBalance(0);
                user.setRedeemQuota(USER_REDEEM_LIMIT);
                user.setRedeemUsed(0);
                user.setContactName(displayName);
                user.setContactPhone(phone);
                user.setContactAddress("请在“我的”页面维护收货地址");
                user.setPasswordHash(authService.encode("P" + UUID.randomUUID().toString().replace("-", "")));
                userAccountRepository.save(user);
                created++;
            } else {
                String username = normalize(command.getUsername());
                if (username != null) {
                    UserAccount byUsername = userAccountRepository.findByUsername(username).orElse(null);
                    if (byUsername != null && !byUsername.getId().equals(user.getId())) {
                        errors.add(errorMap(command.getLineNo(), command.getRaw(), "用户名已被其它手机号占用"));
                        continue;
                    }
                    user.setUsername(username);
                }
                user.setDisplayName(displayName);
                user.setPhoneNumber(phone);
                user.setHrCode(hrCode);
                user.setRedeemQuota(USER_REDEEM_LIMIT);
                if (safe(user.getContactName()).isBlank()) {
                    user.setContactName(displayName);
                }
                if (safe(user.getContactPhone()).isBlank()) {
                    user.setContactPhone(phone);
                }
                if (safe(user.getContactAddress()).isBlank()) {
                    user.setContactAddress("请在“我的”页面维护收货地址");
                }
                if (!user.isEnabled()) {
                    user.setEnabled(true);
                }
                userAccountRepository.save(user);
                updated++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("errorCount", errors.size());
        result.put("errors", errors);
        return result;
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

    private Map<String, Object> errorMap(Integer lineNo, String raw, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lineNo", lineNo);
        data.put("raw", raw == null ? "" : raw);
        data.put("message", message);
        return data;
    }

    private UserAccount getUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private SiteConfig getOrCreateSiteConfig() {
        return siteConfigRepository.findById(SiteConfig.DEFAULT_ID)
                .orElseGet(() -> {
                    SiteConfig config = new SiteConfig();
                    config.setId(SiteConfig.DEFAULT_ID);
                    config.setAnnouncementHtml("<p><strong>活动说明</strong></p><p>请从 7 个方案中选择 1 个完成兑换。</p>");
                    config.setHeroImageUrl("");
                    config.setHotline("400-800-2026");
                    return siteConfigRepository.save(config);
                });
    }

    private void assertCanRedeemOnce(UserAccount user) {
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        int used = user.getRedeemUsed() == null ? 0 : user.getRedeemUsed();
        if (used >= USER_REDEEM_LIMIT || exchangeOrderRepository.existsByUserId(user.getId())) {
            throw new BusinessException("每位用户仅可在 7 个方案中选择 1 个兑换");
        }
        user.setRedeemQuota(USER_REDEEM_LIMIT);
    }

    private boolean hasUserRedeemed(UserAccount user) {
        return (user.getRedeemUsed() == null ? 0 : user.getRedeemUsed()) >= USER_REDEEM_LIMIT
                || exchangeOrderRepository.existsByUserId(user.getId());
    }

    private String sanitizeAnnouncementHtml(String html) {
        String source = html == null ? "" : html.trim();
        if (source.isEmpty()) {
            return "<p><strong>活动说明</strong></p><p>请从 7 个方案中选择 1 个完成兑换。</p>";
        }
        Safelist safelist = Safelist.none()
                .addTags("p", "br", "strong", "b", "ul", "ol", "li");
        String cleaned = Jsoup.clean(source, safelist);
        return cleaned.isBlank() ? "<p>暂无公告</p>" : cleaned;
    }

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private String requirePhone(String value, String message) {
        String normalized = requirePhone(value, message, true);
        if (normalized == null) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private String requirePhone(String value, String message, boolean throwOnBlank) {
        String normalized = normalize(value);
        if (normalized == null) {
            if (throwOnBlank) {
                throw new BusinessException(message);
            }
            return null;
        }
        if (!normalized.matches("^1\\d{10}$")) {
            if (throwOnBlank) {
                throw new BusinessException(message);
            }
            return null;
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
        return "JF" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                + String.format("%04d%04d", userId % 10000, suffix);
    }

    private Map<String, Object> toSiteConfigMap(SiteConfig config) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("announcementHtml", config.getAnnouncementHtml());
        data.put("heroImageUrl", config.getHeroImageUrl());
        data.put("hotline", config.getHotline());
        data.put("updatedAt", config.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toItemMap(RewardItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getId());
        data.put("name", item.getName());
        data.put("description", item.getDescription());
        data.put("stock", item.getStock());
        data.put("coverImage", item.getCoverImage());
        data.put("active", item.isActive());
        data.put("sortOrder", item.getSortOrder());
        data.put("soldOut", item.getStock() == null || item.getStock() < 1);
        return data;
    }

    private Map<String, Object> toOrderMap(ExchangeOrder order) {
        UserAccount user = userAccountRepository.findById(order.getUserId()).orElse(null);
        RewardItem item = rewardItemRepository.findById(order.getItemId()).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("userId", order.getUserId());
        data.put("userName", user == null ? "-" : user.getDisplayName());
        data.put("itemId", order.getItemId());
        data.put("itemName", order.getItemName());
        data.put("itemCoverImage", item == null ? null : item.getCoverImage());
        data.put("quantity", order.getQuantity());
        data.put("totalPoints", order.getTotalPoints());
        data.put("status", order.getStatus());
        data.put("recipientName", order.getRecipientName());
        data.put("phone", order.getPhone());
        data.put("address", order.getAddress());
        data.put("shippingCarrier", order.getShippingCarrier());
        data.put("trackingNo", order.getTrackingNo());
        data.put("trackingUrl", order.getTrackingUrl());
        data.put("fulfilledAt", order.getFulfilledAt());
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
        int quota = user.getRole() == UserRole.USER ? USER_REDEEM_LIMIT : (user.getRedeemQuota() == null ? 0 : user.getRedeemQuota());
        int used = hasUserRedeemed(user) ? USER_REDEEM_LIMIT : 0;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        data.put("phoneNumber", user.getPhoneNumber());
        data.put("hrCode", user.getHrCode());
        data.put("contactName", user.getContactName());
        data.put("contactPhone", user.getContactPhone());
        data.put("contactAddress", user.getContactAddress());
        data.put("role", user.getRole());
        data.put("redeemQuota", quota);
        data.put("redeemUsed", used);
        data.put("redeemRemaining", Math.max(0, quota - used));
        data.put("hasRedeemed", used > 0);
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

    public static class CheckoutItemCommand {
        private Long itemId;
        private Integer quantity;

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
    }

    public static class CheckoutCommand {
        private List<CheckoutItemCommand> items;
        private String recipientName;
        private String phone;
        private String address;

        public List<CheckoutItemCommand> getItems() {
            return items;
        }

        public void setItems(List<CheckoutItemCommand> items) {
            this.items = items;
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

    public static class ContactCommand {
        private String contactName;
        private String contactPhone;
        private String contactAddress;

        public String getContactName() {
            return contactName;
        }

        public void setContactName(String contactName) {
            this.contactName = contactName;
        }

        public String getContactPhone() {
            return contactPhone;
        }

        public void setContactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
        }

        public String getContactAddress() {
            return contactAddress;
        }

        public void setContactAddress(String contactAddress) {
            this.contactAddress = contactAddress;
        }
    }

    public static class SiteConfigCommand {
        private String announcementHtml;
        private String heroImageUrl;
        private String hotline;

        public String getAnnouncementHtml() {
            return announcementHtml;
        }

        public void setAnnouncementHtml(String announcementHtml) {
            this.announcementHtml = announcementHtml;
        }

        public String getHeroImageUrl() {
            return heroImageUrl;
        }

        public void setHeroImageUrl(String heroImageUrl) {
            this.heroImageUrl = heroImageUrl;
        }

        public String getHotline() {
            return hotline;
        }

        public void setHotline(String hotline) {
            this.hotline = hotline;
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

    public static class UserImportCommand {
        private Integer lineNo;
        private String raw;
        private String phoneNumber;
        private String username;
        private String displayName;
        private String hrCode;

        public Integer getLineNo() {
            return lineNo;
        }

        public void setLineNo(Integer lineNo) {
            this.lineNo = lineNo;
        }

        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getHrCode() {
            return hrCode;
        }

        public void setHrCode(String hrCode) {
            this.hrCode = hrCode;
        }
    }
}
