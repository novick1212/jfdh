package com.novick.points.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.novick.points.domain.RewardItem;

public interface RewardItemRepository extends JpaRepository<RewardItem, Long> {

    List<RewardItem> findByActiveTrueOrderBySortOrderAscIdDesc();
}
