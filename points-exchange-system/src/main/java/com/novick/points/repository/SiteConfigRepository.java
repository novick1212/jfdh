package com.novick.points.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.novick.points.domain.SiteConfig;

public interface SiteConfigRepository extends JpaRepository<SiteConfig, Long> {
}
