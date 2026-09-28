package com.shop.order.saga;

import com.shop.events.Events;
import com.shop.events.Topics;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderStatus;
import com.shop.order.repo.OrderRepository;
import com.shop.outbox.IdempotencyGuard;
import com.shop.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Saga orchestrator. Every handler is one local transaction that (1) de-duplicates the incoming event,
 * (2) moves the order state machine, and (3) writes the next command/compensation to the outbox.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orders;
    private final OutboxService outbox;
    private final IdempotencyGuard guard;

    public OrderSagaOrchestrator(OrderRepository orders, OutboxService outbox, IdempotencyGuard guard) {
        this.orders = orders;
        this.outbox = outbox;
        this.guard = guard;
    }

    @Transactional
    public void onInventoryReserved(UUID eventId, Events.InventoryReserved e) {
        if (!guard.firstTime(eventId)) return;
        Order order = load(e.orderId());
        switch (order.getStatus()) {
            case PENDING -> {
                order.changeStatus(OrderStatus.PAYMENT_PENDING, null);
                outbox.enqueue("Order", Topics.PAYMENT_COMMANDS, order.getId().toString(),
                        Topics.Types.PAYMENT_REQUESTED,
                        new Events.PaymentRequested(order.getId(), order.getTotalAmount(),
                                order.getCurrency(), order.getPaymentMethodToken()));
            }
            case CANCELLED -> {
                // Order timed out before stock was confirmed: give the stock straight back.
                log.warn("Inventory reserved for already-cancelled order {}; releasing", order.getId());
                release(order);
            }
            default -> log.warn("Ignoring InventoryReserved for order {} in state {}", order.getId(), order.getStatus());
        }
    }

    @Transactional
    public void onInventoryRejected(UUID eventId, Events.InventoryRejected e) {
        if (!guard.firstTime(eventId)) return;
        Order order = load(e.orderId());
        if (order.getStatus() == OrderStatus.PENDING) {
            order.changeStatus(OrderStatus.REJECTED, e.reason());
        }
    }

    @Transactional
    public void onPaymentSucceeded(UUID eventId, Events.PaymentSucceeded e) {
        if (!guard.firstTime(eventId)) return;
        Order order = load(e.orderId());
        switch (order.getStatus()) {
            case PAYMENT_PENDING -> {
                order.changeStatus(OrderStatus.CONFIRMED, null);
                outbox.enqueue("Order", Topics.INVENTORY_COMMANDS, order.getId().toString(),
                        Topics.Types.COMMIT_RESERVATION, new Events.CommitReservation(order.getId()));
            }
            case CANCELLED -> // Money taken for an order we already cancelled: needs a refund flow (not implemented).
                    log.error("ALERT: payment {} succeeded for CANCELLED order {}; refund required",
                            e.pspReference(), order.getId());
            default -> log.warn("Ignoring PaymentSucceeded for order {} in state {}", order.getId(), order.getStatus());
        }
    }

    @Transactional
    public void onPaymentFailed(UUID eventId, Events.PaymentFailed e) {
        if (!guard.firstTime(eventId)) return;
        Order order = load(e.orderId());
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.changeStatus(OrderStatus.CANCELLED, e.reason());
            release(order);   // compensation
        }
    }

    /** Called by the timeout job; the expected-state check makes it a no-op if the saga already moved on. */
    @Transactional
    public void cancelForTimeout(UUID orderId, OrderStatus expected) {
        Order order = load(orderId);
        if (order.getStatus() != expected) return;
        order.changeStatus(OrderStatus.CANCELLED, "TIMEOUT_IN_" + expected);
        release(order);
    }

    private void release(Order order) {
        outbox.enqueue("Order", Topics.INVENTORY_COMMANDS, order.getId().toString(),
                Topics.Types.RELEASE_RESERVATION, new Events.ReleaseReservation(order.getId()));
    }

    private Order load(UUID id) {
        return orders.findById(id).orElseThrow(() -> new IllegalStateException("Unknown order " + id));
    }
}
