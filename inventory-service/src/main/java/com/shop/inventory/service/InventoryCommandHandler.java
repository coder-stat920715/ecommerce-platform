package com.shop.inventory.service;

import com.shop.events.Events;
import com.shop.inventory.domain.InsufficientStockException;
import com.shop.inventory.domain.ReservationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Concurrency wrapper. On an optimistic-lock conflict the whole transaction is retried with jittered
 * exponential backoff (25ms, 50ms, 100ms... up to 5 attempts) so contending workers spread out.
 * Business failures (insufficient stock) are NOT retried; they turn into an InventoryRejected event.
 * If contention outlasts the retries the exception propagates to the Kafka error handler (backoff, then DLT).
 */
@Component
public class InventoryCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandHandler.class);

    private final InventoryReservationService service;
    private final RetryTemplate retry = RetryTemplate.builder()
            .maxAttempts(5)
            .exponentialBackoff(25, 2.0, 500, true)
            .retryOn(List.of(ObjectOptimisticLockingFailureException.class, CannotAcquireLockException.class))
            .build();

    public InventoryCommandHandler(InventoryReservationService service) {
        this.service = service;
    }

    public void onOrderCreated(UUID eventId, Events.OrderCreated evt) {
        try {
            retry.execute(ctx -> {
                service.reserve(eventId, evt);
                return null;
            });
        } catch (InsufficientStockException e) {
            log.info("Order {} rejected: {}", evt.orderId(), e.getMessage());
            service.reject(eventId, evt.orderId(), e.getMessage());
        }
    }

    public void onCommit(UUID eventId, Events.CommitReservation cmd) {
        retry.execute(ctx -> {
            service.commit(eventId, cmd.orderId());
            return null;
        });
    }

    public void onRelease(UUID eventId, Events.ReleaseReservation cmd) {
        retry.execute(ctx -> {
            service.release(eventId, cmd.orderId(), ReservationStatus.RELEASED);
            return null;
        });
    }

    public void expire(UUID orderId) {
        retry.execute(ctx -> {
            service.release(null, orderId, ReservationStatus.EXPIRED);
            return null;
        });
    }
}
