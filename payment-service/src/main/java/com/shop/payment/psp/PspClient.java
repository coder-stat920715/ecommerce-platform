package com.shop.payment.psp;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the payment provider with Retry -> CircuitBreaker -> Bulkhead. The fallback is attached to the
 * OUTERMOST decorator (Retry) so that transient errors are really retried first, and the fallback only
 * fires once retries are exhausted or the circuit rejects the call.
 * Retrying is safe ONLY because every attempt carries the same Idempotency-Key.
 */
@Component
public class PspClient {

    private static final Logger log = LoggerFactory.getLogger(PspClient.class);

    private final RestClient restClient;

    public PspClient(RestClient pspRestClient) {
        this.restClient = pspRestClient;
    }

    @Retry(name = "pspClient", fallbackMethod = "chargeFallback")
    @CircuitBreaker(name = "pspClient")
    @Bulkhead(name = "pspClient")
    public PspChargeResult charge(PspChargeRequest request, String idempotencyKey) {
        return restClient.post()
                .uri("/v1/charges")
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 402, (req, res) -> {
                    throw new PaymentDeclinedException("PSP_DECLINED");
                })
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw new PaymentDeclinedException("PSP_REJECTED_" + res.getStatusCode().value());
                })
                .body(PspChargeResult.class);
    }

    // Fallbacks: Resilience4j picks the one whose exception type is closest to the thrown exception.

    public PspChargeResult chargeFallback(PspChargeRequest req, String key, PaymentDeclinedException e) {
        return PspChargeResult.declined(e.getCode());
    }

    public PspChargeResult chargeFallback(PspChargeRequest req, String key, CallNotPermittedException e) {
        log.warn("PSP circuit is OPEN; deferring charge {}", key);
        return PspChargeResult.deferred();
    }

    public PspChargeResult chargeFallback(PspChargeRequest req, String key, Exception e) {
        log.warn("PSP outcome unknown for {} ({}); leaving intent PROCESSING for reconciliation", key, e.toString());
        return PspChargeResult.unknown();
    }
}
