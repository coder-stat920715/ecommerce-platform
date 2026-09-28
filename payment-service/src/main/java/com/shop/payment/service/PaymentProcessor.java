package com.shop.payment.service;

import com.shop.events.Events;
import com.shop.outbox.ProcessedEventRepository;
import com.shop.payment.domain.IntentStatus;
import com.shop.payment.domain.PaymentIntent;
import com.shop.payment.psp.PspChargeRequest;
import com.shop.payment.psp.PspChargeResult;
import com.shop.payment.psp.PspClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Charge flow, deliberately NOT wrapped in one transaction:
 *   1. TX: persist intent (PROCESSING)      2. call PSP with idempotency key      3. TX: persist outcome + outbox event
 * If the process dies between 2 and 3 the intent stays PROCESSING and the reconciler finishes the job.
 */
@Service
public class PaymentProcessor {

    private final PaymentPersistence persistence;
    private final PspClient psp;
    private final ProcessedEventRepository processed;

    public PaymentProcessor(PaymentPersistence persistence, PspClient psp, ProcessedEventRepository processed) {
        this.persistence = persistence;
        this.psp = psp;
        this.processed = processed;
    }

    public void handle(UUID eventId, Events.PaymentRequested req) {
        if (processed.existsById(eventId)) return;                       // Kafka redelivery

        PaymentIntent intent;
        try {
            intent = persistence.findOrCreate(req);
        } catch (DataIntegrityViolationException race) {                 // concurrent duplicate won the insert
            intent = persistence.findByOrder(req.orderId());
        }

        if (intent.getStatus() != IntentStatus.PROCESSING) {             // already charged/failed: never charge twice
            persistence.markProcessed(eventId);
            return;
        }
        PspChargeResult result = psp.charge(toRequest(intent), intent.getIdempotencyKey());
        persistence.applyResult(eventId, intent.getId(), result);
    }

    /** Also used by the reconciler: re-sending with the same key returns the ORIGINAL outcome. */
    public void reconcile(PaymentIntent intent) {
        PspChargeResult result = psp.charge(toRequest(intent), intent.getIdempotencyKey());
        persistence.applyResult(null, intent.getId(), result);
    }

    private static PspChargeRequest toRequest(PaymentIntent i) {
        return new PspChargeRequest(i.getAmount(), i.getCurrency(), i.getPaymentMethodToken(), i.getOrderId().toString());
    }
}
