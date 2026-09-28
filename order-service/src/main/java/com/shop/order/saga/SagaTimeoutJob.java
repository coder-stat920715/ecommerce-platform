package com.shop.order.saga;

import com.shop.order.domain.Order;
import com.shop.order.domain.OrderStatus;
import com.shop.order.repo.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Compensates stuck sagas. payment-timeout must be longer than the payment service's reconciliation
 * window, otherwise an order could be cancelled while its charge is still being resolved.
 */
@Component
public class SagaTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutJob.class);

    private final OrderRepository orders;
    private final OrderSagaOrchestrator saga;
    private final Duration pendingTimeout;
    private final Duration paymentTimeout;

    public SagaTimeoutJob(OrderRepository orders, OrderSagaOrchestrator saga,
                          @Value("${saga.pending-timeout:PT5M}") Duration pendingTimeout,
                          @Value("${saga.payment-timeout:PT30M}") Duration paymentTimeout) {
        this.orders = orders;
        this.saga = saga;
        this.pendingTimeout = pendingTimeout;
        this.paymentTimeout = paymentTimeout;
    }

    @Scheduled(fixedDelayString = "${saga.timeout-scan-ms:30000}")
    public void scan() {
        cancelStale(OrderStatus.PENDING, pendingTimeout);
        cancelStale(OrderStatus.PAYMENT_PENDING, paymentTimeout);
    }

    private void cancelStale(OrderStatus status, Duration timeout) {
        for (Order o : orders.findTop100ByStatusAndUpdatedAtBefore(status, Instant.now().minus(timeout))) {
            try {
                saga.cancelForTimeout(o.getId(), status);
                log.warn("Order {} timed out in {}", o.getId(), status);
            } catch (Exception e) {   // e.g. optimistic-lock conflict with a concurrent event: next scan retries
                log.info("Timeout cancel skipped for order {}: {}", o.getId(), e.getMessage());
            }
        }
    }
}
