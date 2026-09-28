package com.shop.order.api;

import com.shop.order.domain.Order;
import com.shop.order.repo.OrderRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** X-User-Id is injected by the API gateway from the validated JWT (client-sent values are stripped there). */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final OrderRepository orders;

    public CheckoutController(CheckoutService checkoutService, OrderRepository orders) {
        this.checkoutService = checkoutService;
        this.orders = orders;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@RequestHeader("X-User-Id") UUID userId,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody CheckoutRequest request) {
        CheckoutService.Result result = checkoutService.checkout(userId, idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(result.response());
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public CheckoutResponse get(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
        Order order = orders.findById(id)
                .filter(o -> o.getCustomerId().equals(userId))   // never reveal other users' orders
                .orElseThrow(() -> new ApiExceptions.OrderNotFound(id));
        return CheckoutResponse.from(order);
    }
}
