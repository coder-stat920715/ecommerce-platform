package com.shop.inventory.domain;

/** Business failure (not retryable): the SKU does not exist or has too little available stock. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku) {
        super("Insufficient stock for " + sku);
    }
}
