package com.shop.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID orderId;
    private String sku;
    private int quantity;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private Instant expiresAt;
    private Instant createdAt;

    protected Reservation() {}

    public static Reservation held(UUID orderId, String sku, int quantity, Duration ttl) {
        Reservation r = new Reservation();
        r.orderId = orderId;
        r.sku = sku;
        r.quantity = quantity;
        r.status = ReservationStatus.RESERVED;
        r.createdAt = Instant.now();
        r.expiresAt = r.createdAt.plus(ttl);
        return r;
    }

    public void setStatus(ReservationStatus status) { this.status = status; }

    public UUID getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
}
