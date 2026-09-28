package com.shop.payment.repo;

import com.shop.payment.domain.IntentStatus;
import com.shop.payment.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByOrderId(UUID orderId);

    List<PaymentIntent> findTop100ByStatusAndUpdatedAtBefore(IntentStatus status, Instant cutoff);
}
