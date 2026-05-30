package com.novick.points.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.novick.points.domain.ExchangeOrder;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrder, Long> {

    List<ExchangeOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ExchangeOrder> findAllByOrderByCreatedAtDesc();
}
