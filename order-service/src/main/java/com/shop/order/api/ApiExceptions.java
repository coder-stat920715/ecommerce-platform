package com.shop.order.api;

public final class ApiExceptions {
    private ApiExceptions() {}

    /** Same Idempotency-Key reused with a different request body. */
    public static class IdempotencyConflict extends RuntimeException {
        public IdempotencyConflict() { super("Idempotency-Key was already used with a different request body"); }
    }

    public static class UnprocessableOrder extends RuntimeException {
        public UnprocessableOrder(String message) { super(message); }
    }

    public static class OrderNotFound extends RuntimeException {
        public OrderNotFound(Object id) { super("Order " + id + " not found"); }
    }
}
