package com.paypilot.payment.outbox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Infra profile: publishes to Kafka, keyed by payment id so one payment's events stay in order. */
@Component
@Profile("infra")
public class KafkaEventTransport implements EventTransport {

    private final KafkaTemplate<String, String> kafka;

    public KafkaEventTransport(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void deliver(List<OutboxEvent> events) {
        try {
            CompletableFuture<?>[] sends = events.stream()
                    .map(e -> kafka.send(e.getTopic(), e.getAggregateId(), e.getPayload()))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(sends).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventDeliveryException("Interrupted while publishing to Kafka", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new EventDeliveryException("Kafka publish failed", e);
        }
    }

    @Override
    public String name() {
        return "kafka";
    }
}
