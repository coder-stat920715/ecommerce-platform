package com.shop.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent-consumer guard. Call inside the same transaction as the business change:
 * the processed-event row commits (or rolls back) together with the state it protects.
 */
@Component
public class IdempotencyGuard {

    private final ProcessedEventRepository repository;

    public IdempotencyGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /** @return true if this is the first time the event is seen, false for a duplicate delivery. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId) {
        if (repository.existsById(eventId)) return false;
        repository.save(new ProcessedEvent(eventId));
        return true;
    }
}
