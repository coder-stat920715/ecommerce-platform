package com.shop.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an event row in the caller's transaction. MANDATORY propagation guarantees
 * the event is committed atomically with the business change (no dual write).
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String aggregateType, String topic, String aggregateId,
                        String eventType, Object payload) {
        try {
            repository.save(new OutboxEvent(aggregateType, aggregateId, topic, eventType,
                    objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise outbox payload " + eventType, e);
        }
    }
}
