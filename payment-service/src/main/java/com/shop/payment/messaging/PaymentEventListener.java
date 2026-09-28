package com.shop.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.events.Events;
import com.shop.events.Topics;
import com.shop.outbox.EventHeaders;
import com.shop.payment.service.PaymentProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final ObjectMapper mapper;
    private final PaymentProcessor processor;

    public PaymentEventListener(ObjectMapper mapper, PaymentProcessor processor) {
        this.mapper = mapper;
        this.processor = processor;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service")
    public void onCommand(ConsumerRecord<String, String> record) throws Exception {
        if (Topics.Types.PAYMENT_REQUESTED.equals(EventHeaders.type(record))) {
            processor.handle(EventHeaders.eventId(record),
                    mapper.readValue(record.value(), Events.PaymentRequested.class));
        } else {
            log.debug("Ignoring payment command type {}", EventHeaders.type(record));
        }
    }
}
