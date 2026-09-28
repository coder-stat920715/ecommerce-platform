package com.shop.outbox;

import com.shop.events.Topics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/** Declares the saga topics (idempotent; created on startup if missing). */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    KafkaAdmin.NewTopics sagaTopics(@Value("${outbox.topic-partitions:3}") int partitions,
                                    @Value("${outbox.topic-replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                topic(Topics.ORDER_EVENTS, partitions, replicas),
                topic(Topics.INVENTORY_EVENTS, partitions, replicas),
                topic(Topics.INVENTORY_COMMANDS, partitions, replicas),
                topic(Topics.PAYMENT_COMMANDS, partitions, replicas),
                topic(Topics.PAYMENT_EVENTS, partitions, replicas));
    }

    private static org.apache.kafka.clients.admin.NewTopic topic(String name, int partitions, int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
