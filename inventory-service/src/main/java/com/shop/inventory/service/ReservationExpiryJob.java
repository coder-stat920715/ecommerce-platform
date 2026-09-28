package com.shop.inventory.service;

import com.shop.inventory.repo.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Releases holds past their TTL so abandoned checkouts never strand stock. */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationRepository reservations;
    private final InventoryCommandHandler handler;

    public ReservationExpiryJob(ReservationRepository reservations, InventoryCommandHandler handler) {
        this.reservations = reservations;
        this.handler = handler;
    }

    @Scheduled(fixedDelayString = "${inventory.expiry-scan-ms:30000}")
    public void sweep() {
        for (UUID orderId : reservations.findOrdersWithExpiredHolds(Instant.now())) {
            try {
                handler.expire(orderId);
                log.warn("Expired stock hold for order {}", orderId);
            } catch (Exception e) {
                log.info("Expiry skipped for order {}: {}", orderId, e.getMessage());
            }
        }
    }
}
