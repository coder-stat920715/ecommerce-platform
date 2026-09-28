package com.shop.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotEmpty @Valid List<Item> items,
        @NotNull UUID shippingAddressId,
        @NotNull @Valid Payment payment) {

    public record Item(@NotBlank String sku, @Min(1) int quantity) {}

    public record Payment(@NotBlank String method,
                          @NotBlank String paymentMethodToken,
                          @NotBlank @Size(min = 3, max = 3) String currency) {}
}
