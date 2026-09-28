package com.shop.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.events.Events;
import com.shop.events.Topics;
import com.shop.inventory.service.InventoryCommandHandler;
import com.shop.outbox.EventHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final ObjectMapper mapper;
    private final InventoryCommandHandler handler;

    public InventoryEventListener(ObjectMapper mapper, InventoryCommandHandler handler) {
        this.mapper = mapper;
        this.handler = handler;
    }

    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.INVENTORY_COMMANDS}, groupId = "inventory-service")
    public void onMessage(ConsumerRecord<String, String> record) throws Exception {
        UUID id = EventHeaders.eventId(record);
        switch (EventHeaders.type(record)) {
            case Topics.Types.ORDER_CREATED ->
                    handler.onOrderCreated(id, mapper.readValue(record.value(), Events.OrderCreated.class));
            case Topics.Types.COMMIT_RESERVATION ->
                    handler.onCommit(id, mapper.readValue(record.value(), Events.CommitReservation.class));
            case Topics.Types.RELEASE_RESERVATION ->
                    handler.onRelease(id, mapper.readValue(record.value(), Events.ReleaseReservation.class));
            default -> log.debug("Ignoring event type {}", EventHeaders.type(record));
        }
    }
}
