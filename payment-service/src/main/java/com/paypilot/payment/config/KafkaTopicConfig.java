package com.paypilot.payment.config;

import com.paypilot.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("infra")
public class KafkaTopicConfig {

    /** Spring's KafkaAdmin creates this topic on startup if it does not exist yet. */
    @Bean
    NewTopic paymentEventsTopic(@Value("${paypilot.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS)
                .partitions(Topics.PAYMENT_EVENTS_PARTITIONS)
                .replicas(replicas)
                .build();
    }
}
