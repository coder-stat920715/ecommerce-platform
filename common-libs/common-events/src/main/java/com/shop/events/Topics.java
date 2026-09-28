package com.shop.events;

/** Kafka topic names and event-type discriminators (sent in the "event-type" header). */
public final class Topics {
    private Topics() {}

    public static final String ORDER_EVENTS = "order.events";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String INVENTORY_COMMANDS = "inventory.commands";
    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String PAYMENT_EVENTS = "payment.events";

    public static final class Types {
        private Types() {}
        public static final String ORDER_CREATED = "OrderCreated";
        public static final String INVENTORY_RESERVED = "InventoryReserved";
        public static final String INVENTORY_REJECTED = "InventoryRejected";
        public static final String COMMIT_RESERVATION = "CommitReservation";
        public static final String RELEASE_RESERVATION = "ReleaseReservation";
        public static final String PAYMENT_REQUESTED = "PaymentRequested";
        public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
        public static final String PAYMENT_FAILED = "PaymentFailed";
    }
}
