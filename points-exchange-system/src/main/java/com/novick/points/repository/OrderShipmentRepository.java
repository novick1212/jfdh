package com.novick.points.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.novick.points.domain.OrderShipment;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, Long> {
    
    List<OrderShipment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<OrderShipment> findByOrderIdInOrderByCreatedAtAsc(Collection<Long> orderIds);
    
    Optional<OrderShipment> findByTrackingNo(String trackingNo);
    
    boolean existsByOrderId(Long orderId);
}