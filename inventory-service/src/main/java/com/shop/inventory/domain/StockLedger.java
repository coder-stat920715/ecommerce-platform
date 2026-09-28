package com.shop.inventory.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "stock_ledger")
public class StockLedger {

    @Id
    private String sku;
    private int availableQty;
    private int reservedQty;
    @Version
    private Long version;          // Hibernate adds "WHERE version = ?" to every UPDATE
    private Instant updatedAt;

    protected StockLedger() {}

    public StockLedger(String sku, int availableQty) {
        this.sku = sku;
        this.availableQty = availableQty;
    }

    /** available -> reserved. */
    public void reserve(int qty) {
        if (availableQty < qty) throw new InsufficientStockException(sku);
        availableQty -= qty;
        reservedQty += qty;
        updatedAt = Instant.now();
    }

    /** Compensation: reserved -> available. */
    public void release(int qty) {
        if (reservedQty < qty) throw new IllegalStateException("Release exceeds reserved for " + sku);
        availableQty += qty;
        reservedQty -= qty;
        updatedAt = Instant.now();
    }

    /** Sale finalised: reserved stock leaves the warehouse. */
    public void commit(int qty) {
        if (reservedQty < qty) throw new IllegalStateException("Commit exceeds reserved for " + sku);
        reservedQty -= qty;
        updatedAt = Instant.now();
    }

    public String getSku() { return sku; }
    public int getAvailableQty() { return availableQty; }
    public int getReservedQty() { return reservedQty; }
}
