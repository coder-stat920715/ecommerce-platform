package com.shop.inventory.repo;

import com.shop.inventory.domain.StockLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLedgerRepository extends JpaRepository<StockLedger, String> {

    /**
     * Hot-SKU path: one atomic statement, the guard lives in the WHERE clause, and the row lock is held
     * only for the statement itself, so there are no version conflicts to retry.
     * Returns 0 when the SKU is unknown or has insufficient stock.
     */
    @Modifying
    @Query("""
            UPDATE StockLedger s
               SET s.availableQty = s.availableQty - :qty,
                   s.reservedQty  = s.reservedQty + :qty,
                   s.version      = s.version + 1
             WHERE s.sku = :sku AND s.availableQty >= :qty
            """)
    int tryReserve(@Param("sku") String sku, @Param("qty") int qty);
}
