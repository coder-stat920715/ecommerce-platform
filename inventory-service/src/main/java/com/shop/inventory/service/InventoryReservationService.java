package com.shop.inventory.service;

import com.shop.events.Events;
import com.shop.events.Topics;
import com.shop.inventory.domain.InsufficientStockException;
import com.shop.inventory.domain.Reservation;
import com.shop.inventory.domain.ReservationStatus;
import com.shop.inventory.domain.StockLedger;
import com.shop.inventory.repo.ReservationRepository;
import com.shop.inventory.repo.StockLedgerRepository;
import com.shop.outbox.IdempotencyGuard;
import com.shop.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Each public method is ONE database transaction covering the stock change, the reservation rows, the
 * outbox event and the processed-event marker, so they commit or roll back together.
 * Optimistic-lock retries are done OUTSIDE the transaction by {@link InventoryCommandHandler}.
 */
@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private final StockLedgerRepository ledgerRepo;
    private final ReservationRepository reservationRepo;
    private final OutboxService outbox;
    private final IdempotencyGuard guard;
    private final Duration ttl;
    private final boolean atomic;

    public InventoryReservationService(StockLedgerRepository ledgerRepo, ReservationRepository reservationRepo,
                                       OutboxService outbox, IdempotencyGuard guard,
                                       @Value("${inventory.reservation.ttl:PT45M}") Duration ttl,
                                       @Value("${inventory.reservation.atomic:false}") boolean atomic) {
        this.ledgerRepo = ledgerRepo;
        this.reservationRepo = reservationRepo;
        this.outbox = outbox;
        this.guard = guard;
        this.ttl = ttl;
        this.atomic = atomic;
    }

    /** All-or-nothing across every line of the order. Throws InsufficientStockException -> full rollback. */
    @Transactional
    public void reserve(UUID eventId, Events.OrderCreated evt) {
        if (!guard.firstTime(eventId)) return;

        // Always lock rows in SKU order so two multi-item orders can never deadlock each other.
        List<Events.OrderLine> lines = evt.items().stream()
                .sorted(Comparator.comparing(Events.OrderLine::sku)).toList();

        for (Events.OrderLine line : lines) {
            if (atomic) {
                if (ledgerRepo.tryReserve(line.sku(), line.quantity()) == 0) {
                    throw new InsufficientStockException(line.sku());
                }
            } else {
                StockLedger ledger = ledgerRepo.findById(line.sku())
                        .orElseThrow(() -> new InsufficientStockException(line.sku()));
                ledger.reserve(line.quantity());   // flush issues UPDATE ... WHERE version = ?
            }
            reservationRepo.save(Reservation.held(evt.orderId(), line.sku(), line.quantity(), ttl));
        }
        outbox.enqueue("Inventory", Topics.INVENTORY_EVENTS, evt.orderId().toString(),
                Topics.Types.INVENTORY_RESERVED, new Events.InventoryReserved(evt.orderId()));
    }

    /** Runs in its own transaction after a business failure rolled back {@link #reserve}. */
    @Transactional
    public void reject(UUID eventId, UUID orderId, String reason) {
        if (!guard.firstTime(eventId)) return;
        outbox.enqueue("Inventory", Topics.INVENTORY_EVENTS, orderId.toString(),
                Topics.Types.INVENTORY_REJECTED, new Events.InventoryRejected(orderId, reason));
    }

    /** Payment succeeded: reserved stock leaves the warehouse. */
    @Transactional
    public void commit(UUID eventId, UUID orderId) {
        if (!guard.firstTime(eventId)) return;
        List<Reservation> held = sortedHolds(orderId);
        if (held.isEmpty()) {
            log.error("ALERT: commit for order {} but no active reservation (expired/released?)", orderId);
            return;
        }
        for (Reservation r : held) {
            ledgerRepo.findById(r.getSku()).orElseThrow().commit(r.getQuantity());
            r.setStatus(ReservationStatus.COMMITTED);
        }
    }

    /** Compensation (payment failed / timeout) or TTL expiry: put the stock back. Idempotent. */
    @Transactional
    public void release(UUID eventIdOrNull, UUID orderId, ReservationStatus target) {
        if (eventIdOrNull != null && !guard.firstTime(eventIdOrNull)) return;
        for (Reservation r : sortedHolds(orderId)) {
            ledgerRepo.findById(r.getSku()).orElseThrow().release(r.getQuantity());
            r.setStatus(target);
        }
    }

    private List<Reservation> sortedHolds(UUID orderId) {
        return reservationRepo.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED).stream()
                .sorted(Comparator.comparing(Reservation::getSku)).toList();
    }
}
