package com.shop.order.domain;

/**
 * PENDING -> PAYMENT_PENDING -> CONFIRMED
 *    |              |
 *    v              v
 * REJECTED       CANCELLED   (stock released by compensation)
 */
public enum OrderStatus {
    PENDING, PAYMENT_PENDING, CONFIRMED, CANCELLED, REJECTED
}
