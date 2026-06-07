package com.novick.points.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.novick.points.domain.ExchangeOrder;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrder, Long> {

    List<ExchangeOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ExchangeOrder> findAllByOrderByCreatedAtDesc();

    boolean existsByUserId(Long userId);

    boolean existsByItemId(Long itemId);

    @Query("SELECT DISTINCT o FROM ExchangeOrder o LEFT JOIN FETCH OrderShipment s ON s.orderId = o.id WHERE o.userId = :userId ORDER BY o.createdAt DESC")
    List<ExchangeOrder> findByUserIdWithShipments(@Param("userId") Long userId);

    @Query("SELECT DISTINCT o FROM ExchangeOrder o LEFT JOIN FETCH OrderShipment s ON s.orderId = o.id ORDER BY o.createdAt DESC")
    List<ExchangeOrder> findAllWithShipments();

    List<ExchangeOrder> findByUserIdInOrderByCreatedAtDesc(Collection<Long> userIds);
}
