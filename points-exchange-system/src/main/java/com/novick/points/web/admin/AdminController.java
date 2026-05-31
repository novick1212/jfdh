package com.novick.points.web.admin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novick.points.common.ApiResponse;
import com.novick.points.domain.UserRole;
import com.novick.points.security.SessionAuthService;
import com.novick.points.service.MallService;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final MallService mallService;
    private final SessionAuthService sessionAuthService;

    public AdminController(MallService mallService, SessionAuthService sessionAuthService) {
        this.mallService = mallService;
        this.sessionAuthService = sessionAuthService;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success(mallService.adminSummary());
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
        command.setPointsCost(request.getPointsCost());
        command.setStock(request.getStock());
        command.setCoverImage(request.getCoverImage());
        command.setActive(request.isActive());
        command.setSortOrder(request.getSortOrder());
        return ApiResponse.success("保存成功", mallService.saveItem(command));
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

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success(mallService.listUsers());
    }

    @PostMapping("/users/{id}/points-adjust")
    public ApiResponse<Map<String, Object>> adjustPoints(@PathVariable Long id,
            @Valid @RequestBody AdjustPointsRequest request, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        return ApiResponse.success("积分已调整", mallService.adjustPoints(id, request.getDelta(), request.getNote()));
    }

    @PostMapping("/users/import-csv")
    public ApiResponse<Map<String, Object>> importUsersCsv(@RequestParam("file") MultipartFile file, HttpSession session) {
        sessionAuthService.requireRole(session, UserRole.ADMIN);
        if (file == null || file.isEmpty()) {
            return ApiResponse.failure("请选择 CSV 文件");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<MallService.UserImportCommand> users = parseCsv(content);
            return ApiResponse.success("导入完成", mallService.importUsers(users));
        } catch (Exception e) {
            return ApiResponse.failure("导入失败：" + e.getMessage());
        }
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
            if (parts.length >= 1) {
                cmd.setPhoneNumber(parts[0]);
            }
            if (parts.length >= 2) {
                cmd.setUsername(parts[1]);
            }
            if (parts.length >= 3) {
                cmd.setDisplayName(parts[2]);
            }
            return cmd;
        }).collect(Collectors.toList());
    }

    private boolean looksLikeHeader(String line) {
        String normalized = (line == null ? "" : line).toLowerCase();
        return normalized.contains("phone") || normalized.contains("手机号") || normalized.contains("username") || normalized.contains("用户名");
    }

    public static class SaveItemRequest {
        private Long id;

        @NotBlank(message = "请输入商品名称")
        private String name;

        @NotBlank(message = "请输入商品描述")
        private String description;

        @NotNull(message = "请输入兑换积分")
        private Integer pointsCost;

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
}
