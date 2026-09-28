package com.shop.payment.psp;

import java.math.BigDecimal;

public record PspChargeRequest(BigDecimal amount, String currency, String paymentMethodToken, String reference) {}
