package com.novick.points.web.app;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ApiResponse<List<Map<String, Object>>> items() {
        return ApiResponse.success(mallService.listActiveItems());
    }

    @GetMapping("/orders")
    public ApiResponse<List<Map<String, Object>>> orders(HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        return ApiResponse.success(mallService.listUserOrders(principal.getUserId()));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<Map<String, Object>>> transactions(HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        return ApiResponse.success(mallService.listUserTransactions(principal.getUserId()));
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

    public static class CreateOrderRequest {
        @NotNull(message = "请选择商品")
        private Long itemId;

        @NotNull(message = "请输入数量")
        @Min(value = 1, message = "数量必须大于 0")
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
}
