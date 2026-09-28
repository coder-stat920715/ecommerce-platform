package com.shop.payment.psp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local stand-in for a real PSP. Behaves like Stripe/Razorpay regarding idempotency: the first request per
 * Idempotency-Key computes and stores the result; every repeat returns the stored result. Not routed by the gateway.
 *
 * Magic tokens:  tok_decline -> 402 card declined
 *                tok_timeout -> charge succeeds but the FIRST response is delayed 6s (client times out and retries;
 *                               the retry gets the original result with no second charge)
 */
@RestController
@RequestMapping("/mock-psp/v1")
@ConditionalOnProperty(name = "psp.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockPspController {

    private final Map<String, PspChargeResult> byIdempotencyKey = new ConcurrentHashMap<>();

    @PostMapping("/charges")
    public ResponseEntity<?> charge(@RequestHeader("Idempotency-Key") String key,
                                    @RequestBody PspChargeRequest request) throws InterruptedException {
        boolean first = !byIdempotencyKey.containsKey(key);
        PspChargeResult result = byIdempotencyKey.computeIfAbsent(key, k ->
                "tok_decline".equals(request.paymentMethodToken())
                        ? PspChargeResult.declined("card_declined")
                        : new PspChargeResult(PspChargeResult.Status.SUCCEEDED, "psp_" + UUID.randomUUID(), null));

        if (first && "tok_timeout".equals(request.paymentMethodToken())) {
            Thread.sleep(6_000);
        }
        if (result.status() == PspChargeResult.Status.DECLINED) {
            return ResponseEntity.status(402).body(Map.of("code", result.failureCode()));
        }
        return ResponseEntity.ok(result);
    }
}
