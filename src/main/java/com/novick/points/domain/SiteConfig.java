package com.novick.points.domain;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "site_config")
public class SiteConfig {

    public static final Long DEFAULT_ID = 1L;

    @Id
    private Long id = DEFAULT_ID;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String announcementHtml;

    @Column(nullable = false, length = 2048)
    private String heroImageUrl;

    @Column(nullable = false, length = 50)
    private String hotline;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.id == null) {
            this.id = DEFAULT_ID;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
