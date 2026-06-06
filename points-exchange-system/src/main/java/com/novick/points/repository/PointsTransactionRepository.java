package com.novick.points.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.novick.points.domain.PointsTransaction;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    List<PointsTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PointsTransaction> findTop20ByOrderByCreatedAtDesc();
}
