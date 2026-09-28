package com.shop.inventory.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockLedgerTest {

    @Test
    void reserveMovesStockFromAvailableToReserved() {
        StockLedger l = new StockLedger("SKU-1", 10);
        l.reserve(4);
        assertEquals(6, l.getAvailableQty());
        assertEquals(4, l.getReservedQty());
    }

    @Test
    void reserveBeyondAvailableFails() {
        StockLedger l = new StockLedger("SKU-1", 3);
        assertThrows(InsufficientStockException.class, () -> l.reserve(4));
        assertEquals(3, l.getAvailableQty());
    }

    @Test
    void releaseRestoresAvailability() {
        StockLedger l = new StockLedger("SKU-1", 10);
        l.reserve(5);
        l.release(5);
        assertEquals(10, l.getAvailableQty());
        assertEquals(0, l.getReservedQty());
    }

    @Test
    void commitConsumesReservedStock() {
        StockLedger l = new StockLedger("SKU-1", 10);
        l.reserve(5);
        l.commit(5);
        assertEquals(5, l.getAvailableQty());
        assertEquals(0, l.getReservedQty());
    }
}
