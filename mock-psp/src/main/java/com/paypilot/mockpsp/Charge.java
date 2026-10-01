package com.paypilot.mockpsp;

import java.time.Instant;

/** A charge as the PSP stores it. Also used as the API response body. */
public record Charge(
        String pspReference,
        String reference,
        long amountMinor,
        String currency,
        Status status,
        String declineCode,
        Instant createdAt) {

    public enum Status {
        SUCCEEDED,
        DECLINED,
        REFUNDED
    }

    Charge withStatus(Status newStatus) {
        return new Charge(pspReference, reference, amountMinor, currency, newStatus, declineCode, createdAt);
    }
}
