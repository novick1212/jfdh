package com.novick.points.web;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.novick.points.common.ApiResponse;
import com.novick.points.security.CsrfTokenUtil;
import com.novick.points.security.LoginAttemptService;
import com.novick.points.security.SessionAuthService;
import com.novick.points.security.SessionPrincipal;
import com.novick.points.service.AuthService;
import com.novick.points.service.SmsCodeService;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SmsCodeService smsCodeService;
    private final SessionAuthService sessionAuthService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthService authService, SmsCodeService smsCodeService,
            SessionAuthService sessionAuthService, LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.smsCodeService = smsCodeService;
        this.sessionAuthService = sessionAuthService;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/csrf-token")
    public ApiResponse<Map<String, String>> csrfToken(HttpSession session) {
        String token = CsrfTokenUtil.getOrCreateToken(session);
        return ApiResponse.success(Map.of("csrfToken", token));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        // 暴力破解防护：检查登录频率限制
        loginAttemptService.checkLoginAllowed(request.getUsername());

        try {
            SessionPrincipal principal = authService.authenticate(request.getUsername(), request.getPassword());
            // 登录成功，使用新 session（防止 Session 固定攻击）
            sessionAuthService.login(httpRequest, principal);
            // 清除失败计数
            loginAttemptService.loginSucceeded(request.getUsername());
            return ApiResponse.success("登录成功", authService.profile(principal));
        } catch (Exception e) {
            // 记录失败次数
            loginAttemptService.loginFailed(request.getUsername());
            throw e;
        }
    }

    @PostMapping("/sms-code")
    public ApiResponse<Map<String, Object>> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        return ApiResponse.success("短信功能已关闭", Map.of("disabled", true));
    }

    @PostMapping("/sms-login")
    public ApiResponse<Map<String, Object>> smsLogin(@Valid @RequestBody SmsLoginRequest request,
            HttpServletRequest httpRequest) {
        // 直接验证姓名+人力资源码登录
        authService.assertSmsLoginUser(request.getHrCode(), request.getDisplayName());
        SessionPrincipal principal = authService.authenticateByHrCode(request.getHrCode());
        // 使用新 session（防止 Session 固定攻击）
        sessionAuthService.login(httpRequest, principal);
        return ApiResponse.success("登录成功", authService.profile(principal));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        sessionAuthService.logout(httpRequest);
        return ApiResponse.success("已退出登录", null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpSession session) {
        SessionPrincipal principal = sessionAuthService.requireLogin(session);
        return ApiResponse.success(authService.profile(principal));
    }

    public static class LoginRequest {
        @NotBlank(message = "请输入账号")
        private String username;

        @NotBlank(message = "请输入密码")
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class SmsCodeRequest {
        @NotBlank(message = "请输入姓名")
        private String displayName;

        @NotBlank(message = "请输入人力资源码")
        private String hrCode;

        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
        private String phoneNumber;

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

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
    }

    public static class SmsLoginRequest {
        @NotBlank(message = "请输入姓名")
        private String displayName;

        @NotBlank(message = "请输入人力资源码")
        private String hrCode;

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
