package com.shop.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Polling relay: locks a batch of PENDING rows, publishes each to Kafka (waiting for the broker ack)
 * and flags it PUBLISHED, all in one DB transaction. Delivery is at-least-once, so consumers must
 * be idempotent (see {@link IdempotencyGuard}). Requires @EnableScheduling in the host service.
 * At very high volume replace with Debezium CDC on the outbox table.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                           @Value("${outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockPending(batchSize);
        for (OutboxEvent event : batch) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload());
            record.headers().add(EventHeaders.EVENT_ID, event.getId().toString().getBytes(UTF_8));
            record.headers().add(EventHeaders.EVENT_TYPE, event.getEventType().getBytes(UTF_8));
            try {
                kafka.send(record).get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Stop the batch to preserve ordering; the row stays PENDING and is retried next poll.
                log.error("Outbox publish failed id={} topic={}; will retry", event.getId(), event.getTopic(), e);
                return;
            }
        }
    }

    @Scheduled(cron = "${outbox.purge-cron:0 0 * * * *}")
    @Transactional
    public void purgePublished() {
        int n = repository.deletePublishedOlderThan(Instant.now().minus(Duration.ofDays(3)));
        if (n > 0) log.info("Purged {} published outbox rows", n);
    }
}
