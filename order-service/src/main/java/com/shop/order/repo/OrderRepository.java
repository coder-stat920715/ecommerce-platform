package com.shop.order.repo;

import com.shop.order.domain.Order;
import com.shop.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    List<Order> findTop100ByStatusAndUpdatedAtBefore(OrderStatus status, Instant cutoff);
}
