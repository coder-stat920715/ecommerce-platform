package com.shop.inventory.api;

import com.shop.inventory.repo.StockLedgerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    public record StockView(String sku, int available, int reserved) {}

    private final StockLedgerRepository ledger;

    public InventoryController(StockLedgerRepository ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/{sku}")
    public StockView get(@PathVariable String sku) {
        return ledger.findById(sku)
                .map(l -> new StockView(l.getSku(), l.getAvailableQty(), l.getReservedQty()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown SKU " + sku));
    }
}
