package com.shop.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.events.Events;
import com.shop.events.Topics;
import com.shop.order.saga.OrderSagaOrchestrator;
import com.shop.outbox.EventHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper mapper;
    private final OrderSagaOrchestrator saga;

    public OrderEventListener(ObjectMapper mapper, OrderSagaOrchestrator saga) {
        this.mapper = mapper;
        this.saga = saga;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = "order-service")
    public void onInventoryEvent(ConsumerRecord<String, String> record) throws Exception {
        UUID id = EventHeaders.eventId(record);
        switch (EventHeaders.type(record)) {
            case Topics.Types.INVENTORY_RESERVED ->
                    saga.onInventoryReserved(id, mapper.readValue(record.value(), Events.InventoryReserved.class));
            case Topics.Types.INVENTORY_REJECTED ->
                    saga.onInventoryRejected(id, mapper.readValue(record.value(), Events.InventoryRejected.class));
            default -> log.debug("Ignoring inventory event type {}", EventHeaders.type(record));
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "order-service")
    public void onPaymentEvent(ConsumerRecord<String, String> record) throws Exception {
        UUID id = EventHeaders.eventId(record);
        switch (EventHeaders.type(record)) {
            case Topics.Types.PAYMENT_SUCCEEDED ->
                    saga.onPaymentSucceeded(id, mapper.readValue(record.value(), Events.PaymentSucceeded.class));
            case Topics.Types.PAYMENT_FAILED ->
                    saga.onPaymentFailed(id, mapper.readValue(record.value(), Events.PaymentFailed.class));
            default -> log.debug("Ignoring payment event type {}", EventHeaders.type(record));
        }
    }
}
