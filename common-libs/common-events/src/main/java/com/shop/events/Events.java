package com.shop.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payloads of every saga message. All are immutable records serialised as JSON. */
public final class Events {
    private Events() {}

    public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {}

    public record OrderCreated(UUID orderId, UUID customerId, BigDecimal totalAmount,
                               String currency, List<OrderLine> items) {}

    public record InventoryReserved(UUID orderId) {}

    public record InventoryRejected(UUID orderId, String reason) {}

    public record CommitReservation(UUID orderId) {}

    public record ReleaseReservation(UUID orderId) {}

    public record PaymentRequested(UUID orderId, BigDecimal amount, String currency,
                                   String paymentMethodToken) {}

    public record PaymentSucceeded(UUID orderId, String pspReference) {}

    public record PaymentFailed(UUID orderId, String reason) {}
}
