package com.shop.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Authoritative server-side price list; client-supplied prices are never trusted. */
@Entity
@Table(name = "product_prices")
public class ProductPrice {
    @Id
    private String sku;
    private BigDecimal unitPrice;
    private String currency;

    protected ProductPrice() {}

    public String getSku() { return sku; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
}
