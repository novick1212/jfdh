package com.novick.points.web;

import java.util.Map;

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

    public AuthController(AuthService authService, SmsCodeService smsCodeService, SessionAuthService sessionAuthService) {
        this.authService = authService;
        this.smsCodeService = smsCodeService;
        this.sessionAuthService = sessionAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        SessionPrincipal principal = authService.authenticate(request.getUsername(), request.getPassword());
        sessionAuthService.login(session, principal);
        return ApiResponse.success("登录成功", authService.profile(principal));
    }

    @PostMapping("/sms-code")
    public ApiResponse<Map<String, Object>> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        authService.assertSmsLoginUser(request.getPhoneNumber());
        return ApiResponse.success("验证码已发送", smsCodeService.sendCode(request.getPhoneNumber()));
    }

    @PostMapping("/sms-login")
    public ApiResponse<Map<String, Object>> smsLogin(@Valid @RequestBody SmsLoginRequest request, HttpSession session) {
        smsCodeService.verifyCode(request.getPhoneNumber(), request.getSmsCode());
        SessionPrincipal principal = authService.authenticateByPhone(request.getPhoneNumber());
        sessionAuthService.login(session, principal);
        return ApiResponse.success("登录成功", authService.profile(principal));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        sessionAuthService.logout(session);
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
        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
        private String phoneNumber;

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
    }

    public static class SmsLoginRequest {
        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的手机号")
        private String phoneNumber;

        @NotBlank(message = "请输入验证码")
        @Pattern(regexp = "^\\d{6}$", message = "请输入 6 位验证码")
        private String smsCode;

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getSmsCode() {
            return smsCode;
        }

        public void setSmsCode(String smsCode) {
            this.smsCode = smsCode;
        }
    }
}
