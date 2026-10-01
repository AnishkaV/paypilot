package com.paypilot.common.events;

import java.time.Instant;

/**
 * The single event contract published on {@link Topics#PAYMENT_EVENTS}.
 *
 * <p>Money is always carried in minor units (paise for INR) as a {@code long}. Never use
 * {@code double} for money: 0.1 + 0.2 != 0.3 in floating point.
 *
 * @param eventId     unique id of this event; consumers use it to drop duplicates
 * @param type        what happened
 * @param paymentId   aggregate id, also used as the Kafka message key so all events of one
 *                    payment land on the same partition and stay in order
 * @param merchantId  merchant that owns the payment
 * @param amountMinor amount in minor units
 * @param currency    ISO 4217 code, e.g. INR
 * @param psp         payment service provider that processed it (null for CREATED)
 * @param failureCode reason code when {@code type} is PAYMENT_FAILED, otherwise null
 * @param occurredAt  when the state change happened
 */
public record PaymentEvent(
        String eventId,
        PaymentEventType type,
        String paymentId,
        String merchantId,
        long amountMinor,
        String currency,
        String psp,
        String failureCode,
        Instant occurredAt) {
}
