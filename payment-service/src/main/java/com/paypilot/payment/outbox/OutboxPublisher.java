package com.paypilot.payment.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.paypilot.payment.config.PayPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the outbox table and hands unpublished rows to the {@link EventTransport}.
 *
 * <p>Delivery is at-least-once: if the service dies after the transport accepted an event but
 * before the row is marked published, the event is sent again on the next run. That is why every
 * consumer must ignore event ids it has already processed.
 *
 * <p>When delivery keeps failing (subscriber down) the publisher backs off exponentially, up to
 * 30 seconds, instead of hammering it every 500 ms.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final OutboxRepository outbox;
    private final EventTransport transport;
    private final TransactionTemplate transaction;
    private final int batchSize;
    private final long pollIntervalMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant pausedUntil = Instant.EPOCH;

    public OutboxPublisher(OutboxRepository outbox, EventTransport transport,
                           TransactionTemplate transaction, PayPilotProperties properties,
                           @Value("${paypilot.outbox.poll-interval-ms:500}") long pollIntervalMs) {
        this.outbox = outbox;
        this.transport = transport;
        this.transaction = transaction;
        this.batchSize = properties.outbox().batchSize();
        this.pollIntervalMs = pollIntervalMs;
    }

    @Scheduled(fixedDelayString = "${paypilot.outbox.poll-interval-ms:500}")
    public void publishPending() {
        if (Instant.now().isBefore(pausedUntil)) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            // SELECT ... FOR UPDATE SKIP LOCKED: several instances can publish without double-sending.
            List<OutboxEvent> batch = outbox.lockNextBatch(batchSize);
            if (batch.isEmpty()) {
                return;
            }
            try {
                transport.deliver(batch);
            } catch (EventTransport.EventDeliveryException e) {
                backOff(batch.size(), e);
                return; // nothing marked published: the whole batch is retried
            }
            batch.forEach(OutboxEvent::markPublished);
            if (consecutiveFailures.getAndSet(0) > 0) {
                log.info("Event delivery via {} recovered", transport.name());
            }
            log.debug("Published {} outbox events via {}", batch.size(), transport.name());
        });
    }

    public String transportName() {
        return transport.name();
    }

    private void backOff(int batchSize, RuntimeException e) {
        int failures = consecutiveFailures.incrementAndGet();
        long delayMs = Math.min(MAX_BACKOFF.toMillis(), pollIntervalMs * (1L << Math.min(failures, 10)));
        pausedUntil = Instant.now().plusMillis(delayMs);
        log.warn("Could not deliver {} outbox events via {} ({}). Retrying in {} ms",
                batchSize, transport.name(), e.getMessage(), delayMs);
    }
}
