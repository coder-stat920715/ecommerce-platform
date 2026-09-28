package com.shop.payment.psp;

public class PaymentDeclinedException extends RuntimeException {
    private final String code;

    public PaymentDeclinedException(String code) {
        super("Payment declined: " + code);
        this.code = code;
    }

    public String getCode() { return code; }
}
