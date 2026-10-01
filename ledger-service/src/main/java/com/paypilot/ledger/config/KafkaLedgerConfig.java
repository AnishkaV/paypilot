package com.paypilot.ledger.config;

import com.paypilot.common.events.Topics;
import com.paypilot.ledger.domain.UnbalancedEntryException;
import com.paypilot.ledger.messaging.PoisonEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Kafka wiring, only in the infra profile. */
@Configuration
@Profile("infra")
public class KafkaLedgerConfig {

    /** Declared here too, so the ledger can start before the payment service has created it. */
    @Bean
    NewTopic paymentEventsTopic(@Value("${paypilot.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS)
                .partitions(Topics.PAYMENT_EVENTS_PARTITIONS)
                .replicas(replicas)
                .build();
    }

    @Bean
    NewTopic paymentEventsDeadLetterTopic(@Value("${paypilot.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS_DLT)
                .partitions(Topics.PAYMENT_EVENTS_PARTITIONS)
                .replicas(replicas)
                .build();
    }

    /**
     * Retry a failing event 3 times, one second apart, then park it on the dead-letter topic so
     * one bad message cannot block its partition forever. Events that can never succeed
     * (unreadable JSON, unbalanced postings) skip the retries.
     *
     * <p>Spring Boot plugs this bean into every @KafkaListener automatically.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(Topics.PAYMENT_EVENTS_DLT, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
        handler.addNotRetryableExceptions(PoisonEventException.class, UnbalancedEntryException.class);
        return handler;
    }
}
