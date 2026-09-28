package com.shop.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents")
public class PaymentIntent {

    @Id
    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private IntentStatus status;
    private String idempotencyKey;
    private String paymentMethodToken;
    private String pspReference;
    private String failureCode;
    @Version
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    protected PaymentIntent() {}

    public PaymentIntent(UUID orderId, BigDecimal amount, String currency, String paymentMethodToken) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethodToken = paymentMethodToken;
        this.idempotencyKey = "pay-" + orderId;      // deterministic: every retry/redelivery reuses it
        this.status = IntentStatus.PROCESSING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void succeed(String pspReference) {
        this.status = IntentStatus.SUCCEEDED;
        this.pspReference = pspReference;
        this.updatedAt = Instant.now();
    }

    public void fail(String failureCode) {
        this.status = IntentStatus.FAILED;
        this.failureCode = failureCode;
        this.updatedAt = Instant.now();
    }

    /** Postpones the next reconciliation attempt. */
    public void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public IntentStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPaymentMethodToken() { return paymentMethodToken; }
    public String getPspReference() { return pspReference; }
    public String getFailureCode() { return failureCode; }
}
