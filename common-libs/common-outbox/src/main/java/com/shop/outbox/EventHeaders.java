package com.shop.outbox;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class EventHeaders {
    private EventHeaders() {}

    public static final String EVENT_ID = "event-id";
    public static final String EVENT_TYPE = "event-type";

    public static UUID eventId(ConsumerRecord<?, ?> record) {
        return UUID.fromString(required(record, EVENT_ID));
    }

    public static String type(ConsumerRecord<?, ?> record) {
        return required(record, EVENT_TYPE);
    }

    private static String required(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        if (h == null) {
            throw new IllegalStateException("Missing header '" + name + "' on " + record.topic() + "@" + record.offset());
        }
        return new String(h.value(), UTF_8);
    }
}
