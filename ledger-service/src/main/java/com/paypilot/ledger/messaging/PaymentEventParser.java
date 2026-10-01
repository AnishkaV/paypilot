package com.paypilot.ledger.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypilot.common.events.PaymentEvent;
import org.springframework.stereotype.Component;

/** Shared by the Kafka listener and the HTTP endpoint, so both paths read events the same way. */
@Component
public class PaymentEventParser {

    private final ObjectMapper objectMapper;

    public PaymentEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param source where the payload came from, used in the error message
     * @throws PoisonEventException if the payload is not a valid payment event
     */
    public PaymentEvent parse(String payload, String source) {
        try {
            PaymentEvent event = objectMapper.readValue(payload, PaymentEvent.class);
            if (event.eventId() == null || event.type() == null || event.paymentId() == null) {
                throw new PoisonEventException("Event at " + source + " is missing eventId, type or paymentId", null);
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new PoisonEventException("Unreadable event at " + source, e);
        }
    }
}
