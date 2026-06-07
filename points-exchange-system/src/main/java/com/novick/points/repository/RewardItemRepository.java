package com.novick.points.repository;

import java.util.List;
import java.util.Optional;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.novick.points.domain.RewardItem;

public interface RewardItemRepository extends JpaRepository<RewardItem, Long> {

    List<RewardItem> findByActiveTrueOrderBySortOrderAscIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RewardItem r WHERE r.id = :id")
    Optional<RewardItem> findByIdForUpdate(@Param("id") Long id);
}
