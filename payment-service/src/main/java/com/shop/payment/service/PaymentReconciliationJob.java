package com.shop.payment.service;

import com.shop.payment.domain.IntentStatus;
import com.shop.payment.domain.PaymentIntent;
import com.shop.payment.repo.PaymentIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Resolves intents whose outcome was unknown or deferred (timeouts, open circuit, crash mid-flight).
 * Safe across multiple instances: the PSP de-duplicates on the idempotency key and applyResult is state-guarded.
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentIntentRepository intents;
    private final PaymentProcessor processor;
    private final Duration reconcileAfter;

    public PaymentReconciliationJob(PaymentIntentRepository intents, PaymentProcessor processor,
                                    @Value("${payment.reconcile-after:PT1M}") Duration reconcileAfter) {
        this.intents = intents;
        this.processor = processor;
        this.reconcileAfter = reconcileAfter;
    }

    @Scheduled(fixedDelayString = "${payment.reconcile-scan-ms:30000}")
    public void reconcile() {
        for (PaymentIntent intent : intents.findTop100ByStatusAndUpdatedAtBefore(
                IntentStatus.PROCESSING, Instant.now().minus(reconcileAfter))) {
            try {
                processor.reconcile(intent);
                log.info("Reconciled payment intent {} for order {}", intent.getId(), intent.getOrderId());
            } catch (Exception e) {
                log.warn("Reconciliation of intent {} failed: {}", intent.getId(), e.toString());
            }
        }
    }
}
