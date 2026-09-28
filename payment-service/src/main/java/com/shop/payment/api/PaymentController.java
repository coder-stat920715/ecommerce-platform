package com.shop.payment.api;

import com.shop.payment.domain.IntentStatus;
import com.shop.payment.repo.PaymentIntentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    public record PaymentView(UUID orderId, IntentStatus status, BigDecimal amount, String currency,
                              String pspReference, String failureCode) {}

    private final PaymentIntentRepository intents;

    public PaymentController(PaymentIntentRepository intents) {
        this.intents = intents;
    }

    @GetMapping("/{orderId}")
    public PaymentView byOrder(@PathVariable UUID orderId) {
        return intents.findByOrderId(orderId)
                .map(i -> new PaymentView(i.getOrderId(), i.getStatus(), i.getAmount(), i.getCurrency(),
                        i.getPspReference(), i.getFailureCode()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for order " + orderId));
    }
}
