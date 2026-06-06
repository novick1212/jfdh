package com.novick.points.web.app;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novick.points.common.ApiResponse;
import com.novick.points.security.SessionAuthService;
import com.novick.points.security.SessionPrincipal;
import com.novick.points.service.MallService;

@Validated
@RestController
@RequestMapping("/api/app")
public class AppController {

    private final MallService mallService;
    private final SessionAuthService sessionAuthService;

    public AppController(MallService mallService, SessionAuthService sessionAuthService) {
        this.mallService = mallService;
        this.sessionAuthService = sessionAuthService;
    }

    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> home(HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        return ApiResponse.success(mallService.appHome(principal));
    }

    @GetMapping("/items")
    public ApiResponse<List<Map<String, Object>>> items(HttpSession session) {
        sessionAuthService.requireLogin(session);
        return ApiResponse.success(mallService.listActiveItems());
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> orders(HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        return ApiResponse.success(mallService.listUserOrders(principal.getUserId()));
    }

    @GetMapping("/orders/{id}/tracking")
    public ApiResponse<Map<String, Object>> orderTracking(@PathVariable Long id, HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        return ApiResponse.success(mallService.getOrderTracking(principal, id));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<Map<String, Object>>> transactions(HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        return ApiResponse.success(mallService.listUserTransactions(principal.getUserId()));
    }

    @PostMapping("/profile/contact")
    public ApiResponse<Map<String, Object>> updateContact(@Valid @RequestBody UpdateContactRequest request, HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        MallService.ContactCommand command = new MallService.ContactCommand();
        command.setContactName(request.getContactName());
        command.setContactPhone(request.getContactPhone());
        command.setContactAddress(request.getContactAddress());
        return ApiResponse.success("保存成功", mallService.updateContactInfo(principal, command));
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest request, HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        MallService.CreateOrderCommand command = new MallService.CreateOrderCommand();
        command.setItemId(request.getItemId());
        command.setQuantity(request.getQuantity());
        command.setRecipientName(request.getRecipientName());
        command.setPhone(request.getPhone());
        command.setAddress(request.getAddress());
        return ApiResponse.success("兑换成功", mallService.createOrder(principal, command));
    }

    @PostMapping("/orders/checkout")
    public ApiResponse<List<Map<String, Object>>> checkout(@Valid @RequestBody CheckoutRequest request, HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        MallService.CheckoutCommand command = new MallService.CheckoutCommand();
        command.setRecipientName(request.getRecipientName());
        command.setPhone(request.getPhone());
        command.setAddress(request.getAddress());
        command.setItems(request.getItems().stream().map(item -> {
            MallService.CheckoutItemCommand c = new MallService.CheckoutItemCommand();
            c.setItemId(item.getItemId());
            c.setQuantity(item.getQuantity());
            return c;
        }).collect(Collectors.toList()));
        return ApiResponse.success("下单成功", mallService.checkout(principal, command));
    }

    public static class CreateOrderRequest {
        @NotNull(message = "请选择商品")
        private Long itemId;

        private Integer quantity;

        @NotBlank(message = "请输入收货人")
        private String recipientName;

        @NotBlank(message = "请输入手机号")
        private String phone;

        @NotBlank(message = "请输入收货地址")
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

    public static class UpdateContactRequest {
        @NotBlank(message = "请输入收货人")
        private String contactName;

        @NotBlank(message = "请输入手机号")
        @javax.validation.constraints.Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
        private String contactPhone;

        @NotBlank(message = "请输入收货地址")
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

    public static class CheckoutRequest {
        @NotNull(message = "购物车为空")
        @Size(min = 1, message = "购物车为空")
        @Valid
        private List<CheckoutItemRequest> items;

        @NotBlank(message = "请输入收货人")
        private String recipientName;

        @NotBlank(message = "请输入手机号")
        private String phone;

        @NotBlank(message = "请输入收货地址")
        private String address;

        public List<CheckoutItemRequest> getItems() {
            return items;
        }

        public void setItems(List<CheckoutItemRequest> items) {
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

    public static class CheckoutItemRequest {
        @NotNull(message = "请选择商品")
        private Long itemId;

        @NotNull(message = "请输入数量")
        @Min(value = 1, message = "数量必须大于 0")
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
}
