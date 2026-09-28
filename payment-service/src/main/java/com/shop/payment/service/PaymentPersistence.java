package com.shop.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.events.Events;
import com.shop.events.Topics;
import com.shop.outbox.IdempotencyGuard;
import com.shop.outbox.OutboxService;
import com.shop.payment.domain.IntentStatus;
import com.shop.payment.domain.PaymentIntent;
import com.shop.payment.domain.PaymentTransaction;
import com.shop.payment.psp.PspChargeResult;
import com.shop.payment.repo.PaymentIntentRepository;
import com.shop.payment.repo.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Short database transactions only. The PSP call itself happens OUTSIDE any transaction (see PaymentProcessor). */
@Service
public class PaymentPersistence {

    private final PaymentIntentRepository intents;
    private final PaymentTransactionRepository transactions;
    private final OutboxService outbox;
    private final IdempotencyGuard guard;
    private final ObjectMapper mapper;

    public PaymentPersistence(PaymentIntentRepository intents, PaymentTransactionRepository transactions,
                              OutboxService outbox, IdempotencyGuard guard, ObjectMapper mapper) {
        this.intents = intents;
        this.transactions = transactions;
        this.outbox = outbox;
        this.guard = guard;
        this.mapper = mapper;
    }

    /** Step 1 - durably record the intent BEFORE talking to the PSP. UNIQUE(order_id) makes this race-safe. */
    @Transactional
    public PaymentIntent findOrCreate(Events.PaymentRequested req) {
        return intents.findByOrderId(req.orderId()).orElseGet(() ->
                intents.saveAndFlush(new PaymentIntent(req.orderId(), req.amount(), req.currency(),
                        req.paymentMethodToken())));
    }

    @Transactional(readOnly = true)
    public PaymentIntent findByOrder(UUID orderId) {
        return intents.findByOrderId(orderId).orElseThrow();
    }

    /** Step 3 - record the outcome, emit the saga event and mark the Kafka message processed, atomically. */
    @Transactional
    public void applyResult(UUID eventIdOrNull, UUID intentId, PspChargeResult result) {
        if (eventIdOrNull != null) guard.firstTime(eventIdOrNull);

        PaymentIntent intent = intents.findById(intentId).orElseThrow();
        if (intent.getStatus() != IntentStatus.PROCESSING) return;   // already resolved by another worker

        transactions.save(new PaymentTransaction(intentId, "CHARGE", result.status().name(),
                intent.getAmount(), json(result)));

        switch (result.status()) {
            case SUCCEEDED -> {
                intent.succeed(result.pspReference());
                outbox.enqueue("Payment", Topics.PAYMENT_EVENTS, intent.getOrderId().toString(),
                        Topics.Types.PAYMENT_SUCCEEDED,
                        new Events.PaymentSucceeded(intent.getOrderId(), result.pspReference()));
            }
            case DECLINED -> {
                intent.fail(result.failureCode());
                outbox.enqueue("Payment", Topics.PAYMENT_EVENTS, intent.getOrderId().toString(),
                        Topics.Types.PAYMENT_FAILED,
                        new Events.PaymentFailed(intent.getOrderId(), result.failureCode()));
            }
            case DEFERRED, UNKNOWN -> intent.touch();   // stays PROCESSING; reconciler retries with the same key
        }
    }

    @Transactional
    public void markProcessed(UUID eventId) {
        guard.firstTime(eventId);
    }

    private String json(PspChargeResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
