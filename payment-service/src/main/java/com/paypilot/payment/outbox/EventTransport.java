package com.paypilot.payment.outbox;

import java.util.List;

/**
 * How outbox events leave this service. The outbox pattern does not care about the transport:
 * locally events go over HTTP, in the infra profile they go to Kafka. Swapping one for the other
 * is a profile switch, not a code change (ports and adapters).
 */
public interface EventTransport {

    /**
     * Delivers the events in the given order.
     *
     * @throws EventDeliveryException if any event could not be delivered. The publisher then
     *                                marks nothing as published and retries the whole batch.
     */
    void deliver(List<OutboxEvent> events);

    /** Shown in logs and on the ops dashboard. */
    String name();

    class EventDeliveryException extends RuntimeException {
        public EventDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
