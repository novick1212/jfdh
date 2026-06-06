package com.novick.points.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.novick.points.common.BusinessException;
import com.novick.points.domain.UserAccount;
import com.novick.points.domain.UserRole;
import com.novick.points.repository.UserAccountRepository;
import com.novick.points.security.SessionPrincipal;

@Service
public class AuthService {

    private static final int USER_REDEEM_LIMIT = 1;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public SessionPrincipal authenticate(String username, String password) {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("账号或密码错误"));
        assertUserEnabled(user);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        return new SessionPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public SessionPrincipal authenticateByPhone(String phoneNumber) {
        UserAccount user = userAccountRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException("手机号未绑定用户"));
        assertUserEnabled(user);
        return new SessionPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public void assertSmsLoginUser(String hrCode, String displayName) {
        UserAccount user = userAccountRepository.findByHrCode(hrCode)
                .orElseThrow(() -> new BusinessException("人力资源码不存在"));
        assertUserEnabled(user);
        String expectedName = user.getDisplayName() == null ? "" : user.getDisplayName().trim();
        if (!expectedName.equals(displayName == null ? "" : displayName.trim())) {
            throw new BusinessException("姓名与人力资源码不匹配");
        }
    }

    public SessionPrincipal authenticateByHrCode(String hrCode) {
        UserAccount user = userAccountRepository.findByHrCode(hrCode)
                .orElseThrow(() -> new BusinessException("人力资源码不存在"));
        assertUserEnabled(user);
        return new SessionPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    private void assertUserEnabled(UserAccount user) {
        if (!user.isEnabled()) {
            throw new BusinessException("账号已禁用");
        }
    }

    public String encode(String password) {
        return passwordEncoder.encode(password);
    }

    public Map<String, Object> updateAdminProfile(Long adminId, String username, String oldPassword, String newPassword) {
        UserAccount admin = userAccountRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException("管理员不存在"));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new BusinessException("无权限操作");
        }

        // 验证旧密码
        if (oldPassword != null && !oldPassword.isBlank()) {
            if (!passwordEncoder.matches(oldPassword, admin.getPasswordHash())) {
                throw new BusinessException("原密码错误");
            }
            if (newPassword == null || newPassword.isBlank()) {
                throw new BusinessException("新密码不能为空");
            }
            if (newPassword.length() < 6) {
                throw new BusinessException("新密码长度不能少于6位");
            }
            admin.setPasswordHash(passwordEncoder.encode(newPassword));
        }

        // 修改用户名
        if (username != null && !username.isBlank()) {
            if (!username.equals(admin.getUsername())) {
                // 检查用户名是否被占用
                if (userAccountRepository.findByUsername(username).isPresent()) {
                    throw new BusinessException("用户名已被占用");
                }
                admin.setUsername(username);
            }
        }

        userAccountRepository.save(admin);
        return profile(new SessionPrincipal(admin.getId(), admin.getUsername(), admin.getDisplayName(), admin.getRole()));
    }

    public Map<String, Object> profile(SessionPrincipal principal) {
        UserAccount user = userAccountRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        int quota = user.getRole() == null || user.getRole() == UserRole.USER
                ? USER_REDEEM_LIMIT
                : (user.getRedeemQuota() == null ? 0 : user.getRedeemQuota());
        int used = Math.min(USER_REDEEM_LIMIT, user.getRedeemUsed() == null ? 0 : user.getRedeemUsed());
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
        return data;
    }
}
