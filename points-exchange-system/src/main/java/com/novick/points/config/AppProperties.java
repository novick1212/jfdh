package com.novick.points.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String legacyStaticPath;
    private String seedAdminUsername;
    private String seedAdminPassword;
    private String seedUserUsername;
    private String seedUserPassword;
    private String seedUserPhone;
    private boolean smsMockEnabled = true;
    private int smsCodeTtlSeconds = 300;
    private int smsResendIntervalSeconds = 60;

    public String getLegacyStaticPath() {
        return legacyStaticPath;
    }

    public void setLegacyStaticPath(String legacyStaticPath) {
        this.legacyStaticPath = legacyStaticPath;
    }

    public String getSeedAdminUsername() {
        return seedAdminUsername;
    }

    public void setSeedAdminUsername(String seedAdminUsername) {
        this.seedAdminUsername = seedAdminUsername;
    }

    public String getSeedAdminPassword() {
        return seedAdminPassword;
    }

    public void setSeedAdminPassword(String seedAdminPassword) {
        this.seedAdminPassword = seedAdminPassword;
    }

    public String getSeedUserUsername() {
        return seedUserUsername;
    }

    public void setSeedUserUsername(String seedUserUsername) {
        this.seedUserUsername = seedUserUsername;
    }

    public String getSeedUserPassword() {
        return seedUserPassword;
    }

    public void setSeedUserPassword(String seedUserPassword) {
        this.seedUserPassword = seedUserPassword;
    }

    public String getSeedUserPhone() {
        return seedUserPhone;
    }

    public void setSeedUserPhone(String seedUserPhone) {
        this.seedUserPhone = seedUserPhone;
    }

    public boolean isSmsMockEnabled() {
        return smsMockEnabled;
    }

    public void setSmsMockEnabled(boolean smsMockEnabled) {
        this.smsMockEnabled = smsMockEnabled;
    }

    public int getSmsCodeTtlSeconds() {
        return smsCodeTtlSeconds;
    }

    public void setSmsCodeTtlSeconds(int smsCodeTtlSeconds) {
        this.smsCodeTtlSeconds = smsCodeTtlSeconds;
    }

    public int getSmsResendIntervalSeconds() {
        return smsResendIntervalSeconds;
    }

    public void setSmsResendIntervalSeconds(int smsResendIntervalSeconds) {
        this.smsResendIntervalSeconds = smsResendIntervalSeconds;
    }
}
