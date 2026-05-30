package com.novick.points.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.novick.points.common.BusinessException;
import com.novick.points.domain.UserAccount;
import com.novick.points.repository.UserAccountRepository;
import com.novick.points.security.SessionPrincipal;

@Service
public class AuthService {

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

    public void assertSmsLoginUser(String phoneNumber) {
        UserAccount user = userAccountRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException("手机号未绑定用户"));
        assertUserEnabled(user);
    }

    private void assertUserEnabled(UserAccount user) {
        if (!user.isEnabled()) {
            throw new BusinessException("账号已禁用");
        }
    }

    public String encode(String password) {
        return passwordEncoder.encode(password);
    }

    public Map<String, Object> profile(SessionPrincipal principal) {
        UserAccount user = userAccountRepository.findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName());
        data.put("phoneNumber", user.getPhoneNumber());
        data.put("role", user.getRole());
        data.put("pointsBalance", user.getPointsBalance());
        return data;
    }
}
