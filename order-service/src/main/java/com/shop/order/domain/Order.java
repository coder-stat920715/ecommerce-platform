package com.shop.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;
    private UUID customerId;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String currency;
    private String idempotencyKey;
    private String requestHash;
    private UUID shippingAddressId;
    private String paymentMethodToken;
    private String failureReason;
    @Version
    private Long version;           // wrapper type so Spring Data treats a fresh instance as "new"
    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(UUID customerId, String idempotencyKey, String requestHash,
                 UUID shippingAddressId, String paymentMethodToken, String currency) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.shippingAddressId = shippingAddressId;
        this.paymentMethodToken = paymentMethodToken;
        this.currency = currency;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addItem(String sku, int quantity, BigDecimal unitPrice) {
        items.add(new OrderItem(this, sku, quantity, unitPrice));
        totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public void changeStatus(OrderStatus newStatus, String reason) {
        this.status = newStatus;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getRequestHash() { return requestHash; }
    public String getPaymentMethodToken() { return paymentMethodToken; }
    public String getFailureReason() { return failureReason; }
    public List<OrderItem> getItems() { return items; }
}
