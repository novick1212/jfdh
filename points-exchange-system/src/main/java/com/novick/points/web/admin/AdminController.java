package com.novick.points.web.admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novick.points.common.ApiResponse;
import com.novick.points.domain.UserRole;
import com.novick.points.security.SessionAuthService;
import com.novick.points.security.SessionPrincipal;
import com.novick.points.service.AuthService;
import com.novick.points.service.MallService;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final MallService mallService;
    private final AuthService authService;
    private final SessionAuthService sessionAuthService;

    public AdminController(MallService mallService, AuthService authService, SessionAuthService sessionAuthService) {
        this.mallService = mallService;
        this.authService = authService;
        this.sessionAuthService = sessionAuthService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success(mallService.adminSummary());
    }

    @PostMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@Valid @RequestBody UpdateProfileRequest request, HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success("修改成功", authService.updateAdminProfile(principal.getUserId(), request.getUsername(), request.getOldPassword(), request.getNewPassword()));
    }

    @GetMapping("/site-config")
    public ApiResponse<Map<String, Object>> siteConfig(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success(mallService.getSiteConfig());
    }

    @PostMapping("/site-config")
    public ApiResponse<Map<String, Object>> saveSiteConfig(@Valid @RequestBody SaveSiteConfigRequest request, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        MallService.SiteConfigCommand command = new MallService.SiteConfigCommand();
        command.setAnnouncementHtml(request.getAnnouncementHtml());
        command.setHeroImageUrl(request.getHeroImageUrl());
        command.setHotline(request.getHotline());
        return ApiResponse.success("保存成功", mallService.saveSiteConfig(command));
    }

    @GetMapping("/items")
    public ApiResponse<List<Map<String, Object>>> items(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success(mallService.listAllItems());
    }

    @PostMapping("/items")
    public ApiResponse<Map<String, Object>> saveItem(@Valid @RequestBody SaveItemRequest request, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        MallService.ItemCommand command = new MallService.ItemCommand();
        command.setId(request.getId());
        command.setName(request.getName());
        command.setDescription(request.getDescription());
        command.setPointsCost(0);
        command.setStock(request.getStock());
        command.setCoverImage(request.getCoverImage());
        command.setActive(request.isActive());
        command.setSortOrder(request.getSortOrder());
        return ApiResponse.success("保存成功", mallService.saveItem(command));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        mallService.deleteItem(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> orders(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success(mallService.listAllOrders());
    }

    @PostMapping("/orders/{id}/fulfill")
    public ApiResponse<Map<String, Object>> fulfillOrder(@PathVariable Long id,
            @Valid @RequestBody FulfillOrderRequest request, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success("发货完成", mallService.fulfillOrder(id, request.getShippingCarrier(), request.getTrackingNo()));
    }

    @PostMapping("/orders/{id}/shipments")
    public ApiResponse<Map<String, Object>> addShipment(@PathVariable Long id,
            @Valid @RequestBody FulfillOrderRequest request, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success("物流已添加", mallService.addShipment(id, request.getShippingCarrier(), request.getTrackingNo()));
    }

    @DeleteMapping("/orders/{orderId}/shipments/{shipmentId}")
    public ApiResponse<Void> deleteShipment(@PathVariable Long orderId, @PathVariable Long shipmentId, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        mallService.deleteShipment(orderId, shipmentId);
        return ApiResponse.success("物流已删除", null);
    }

    @GetMapping("/orders/{orderId}/shipments/{shipmentId}/tracking")
    public ApiResponse<Map<String, Object>> getShipmentTracking(@PathVariable Long orderId, 
            @PathVariable Long shipmentId, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        Map<String, Object> tracking = mallService.getShipmentTracking(orderId, shipmentId);
        return ApiResponse.success(tracking);
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success(mallService.listUsers());
    }

    @GetMapping("/reports/redemption")
    public ResponseEntity<byte[]> exportRedemptionReport(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        byte[] bytes = mallService.exportRedemptionReportXlsx();

        String filename = "redemption-report-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PostMapping("/users/{id}/points-adjust")
    public ApiResponse<Map<String, Object>> adjustPoints(@PathVariable Long id,
            @Valid @RequestBody AdjustPointsRequest request, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success("积分已调整", mallService.adjustPoints(id, request.getDelta(), request.getNote()));
    }

    @PostMapping("/users/{id}/redeem-quota")
    public ApiResponse<Map<String, Object>> setRedeemQuota(@PathVariable Long id,
            @Valid @RequestBody SetRedeemQuotaRequest request, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success("兑换次数已设置", mallService.setRedeemQuota(id, request.getQuota()));
    }

    @PostMapping("/users/import-csv")
    public ApiResponse<Map<String, Object>> importUsersCsv(@RequestParam("file") MultipartFile file, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        if (file == null || file.isEmpty()) {
            return ApiResponse.failure("请选择 CSV 文件");
        }
        try {
            String content = decodeWithCharsetDetection(file.getBytes());
            List<MallService.UserImportCommand> users = parseCsv(content);
            return ApiResponse.success("导入完成", mallService.importUsers(users));
        } catch (Exception e) {
            return ApiResponse.failure("导入失败：" + e.getMessage());
        }
    }

    private String decodeWithCharsetDetection(byte[] bytes) {
        // 先尝试 UTF-8
        String utf8Content = new String(bytes, StandardCharsets.UTF_8);
        if (!looksLikeGarbled(utf8Content)) {
            return utf8Content;
        }
        // 尝试 GBK（中文 Windows 常用）
        try {
            String gbkContent = new String(bytes, "GBK");
            if (!looksLikeGarbled(gbkContent)) {
                return gbkContent;
            }
        } catch (Exception e) {
            // ignore
        }
        // 尝试 GB2312
        try {
            String gb2312Content = new String(bytes, "GB2312");
            if (!looksLikeGarbled(gb2312Content)) {
                return gb2312Content;
            }
        } catch (Exception e) {
            // ignore
        }
        // 回退到 UTF-8
        return utf8Content;
    }

    private boolean looksLikeGarbled(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        // 检测是否包含乱码特征：连续的 ? 或 锟斤拷 等
        if (content.contains("锟斤拷") || content.contains("�")) {
            return true;
        }
        // 检测中文乱码比例
        long chineseCount = content.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        long totalCount = content.length();
        if (totalCount > 0 && chineseCount > 0) {
            // 如果有中文字符但识别率太低，可能是乱码
            return chineseCount < totalCount * 0.1 && totalCount > 50;
        }
        return false;
    }

    private List<MallService.UserImportCommand> parseCsv(String content) {
        if (content == null) {
            return List.of();
        }
        List<String> lines = content.lines()
                .map(line -> line == null ? "" : line.trim())
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            return List.of();
        }

        int startIndex = 0;
        String first = lines.get(0).replace("\uFEFF", "");
        if (looksLikeHeader(first)) {
            startIndex = 1;
        }

        int startLineNo = startIndex + 1;
        List<String> dataLines = lines.subList(startIndex, lines.size());
        return java.util.stream.IntStream.range(0, dataLines.size()).mapToObj(i -> {
            int lineNo = startLineNo + i;
            String raw = dataLines.get(i);

            String normalized = raw.replace('\uFEFF', ' ').trim().replace('，', ',');
            String[] parts = normalized.split("\\s*,\\s*", -1);
            MallService.UserImportCommand cmd = new MallService.UserImportCommand();
            cmd.setLineNo(lineNo);
            cmd.setRaw(raw);
            // 格式：姓名,人力资源码
            if (parts.length >= 1) {
                cmd.setDisplayName(parts[0]);
            }
            if (parts.length >= 2) {
                cmd.setHrCode(parts[1]);
            }
            return cmd;
        }).collect(Collectors.toList());
    }

    private boolean looksLikeHeader(String line) {
        String normalized = (line == null ? "" : line).toLowerCase();
        return normalized.contains("phone") || normalized.contains("手机号") || normalized.contains("username")
                || normalized.contains("用户名") || normalized.contains("hr") || normalized.contains("人力") || normalized.contains("name") || normalized.contains("姓名");
    }

    public static class SaveItemRequest {
        private Long id;

        @NotBlank(message = "请输入商品名称")
        private String name;

        @NotBlank(message = "请输入商品描述")
        private String description;

        @NotNull(message = "请输入库存")
        private Integer stock;

        @NotBlank(message = "请输入封面图地址")
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

    public static class SaveSiteConfigRequest {
        @NotBlank(message = "请输入公告内容")
        private String announcementHtml;

        @NotBlank(message = "请输入活动图片地址")
        private String heroImageUrl;

        @NotBlank(message = "请输入客服热线")
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

    public static class AdjustPointsRequest {
        @NotNull(message = "请输入调整积分")
        private Integer delta;

        private String note;

        public Integer getDelta() {
            return delta;
        }

        public void setDelta(Integer delta) {
            this.delta = delta;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    public static class SetRedeemQuotaRequest {
        @NotNull(message = "请输入兑换次数")
        private Integer quota;

        public Integer getQuota() {
            return quota;
        }

        public void setQuota(Integer quota) {
            this.quota = quota;
        }
    }

    public static class FulfillOrderRequest {
        private String shippingCarrier;
        private String trackingNo;

        public String getShippingCarrier() {
            return shippingCarrier;
        }

        public void setShippingCarrier(String shippingCarrier) {
            this.shippingCarrier = shippingCarrier;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }
    }

    public static class UpdateProfileRequest {
        private String username;
        private String oldPassword;
        private String newPassword;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }
}
