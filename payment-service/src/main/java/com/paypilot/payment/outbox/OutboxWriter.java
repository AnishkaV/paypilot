package com.paypilot.payment.outbox;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypilot.common.events.PaymentEvent;
import com.paypilot.common.events.PaymentEventType;
import com.paypilot.common.events.Topics;
import com.paypilot.payment.domain.Ids;
import com.paypilot.payment.domain.Payment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * MANDATORY: fails fast if there is no surrounding transaction. The whole point of the outbox
     * is that the event row commits (or rolls back) together with the payment change.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Payment payment, PaymentEventType type) {
        PaymentEvent event = new PaymentEvent(
                Ids.newEventId(),
                type,
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getPsp(),
                payment.getFailureCode(),
                Instant.now());
        outbox.save(new OutboxEvent(event.eventId(), payment.getId(), type.name(),
                Topics.PAYMENT_EVENTS, toJson(event), event.occurredAt()));
    }

    private String toJson(PaymentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + event.type(), e);
        }
    }
}
