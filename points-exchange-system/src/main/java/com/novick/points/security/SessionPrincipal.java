package com.novick.points.security;

import com.novick.points.domain.UserRole;

public class SessionPrincipal {

    private final Long userId;
    private final String username;
    private final String displayName;
    private final UserRole role;

    public SessionPrincipal(Long userId, String username, String displayName, UserRole role) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }
}
