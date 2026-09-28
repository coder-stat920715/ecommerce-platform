package com.shop.order.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckoutResponse(UUID orderId, OrderStatus status, BigDecimal totalAmount,
                               String currency, String failureReason, Map<String, String> links) {

    public static CheckoutResponse from(Order o) {
        return new CheckoutResponse(o.getId(), o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                o.getFailureReason(), Map.of("self", "/api/v1/orders/" + o.getId()));
    }
}
