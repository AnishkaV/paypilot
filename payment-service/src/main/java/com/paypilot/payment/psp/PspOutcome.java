package com.paypilot.payment.psp;

/**
 * What we know after talking to a PSP. The difference between NOT_PROCESSED and UNKNOWN is the
 * most important idea in this service: only NOT_PROCESSED makes it safe to try another PSP.
 */
public enum PspOutcome {

    /** The customer was charged. */
    SUCCEEDED(true),

    /** The PSP answered and said no (insufficient funds, limits...). The PSP itself is healthy. */
    DECLINED(true),

    /** We know the charge did not happen: connection refused, 503 before processing, no record. */
    NOT_PROCESSED(false),

    /** The request may or may not have been charged (read timeout, 500). Must be resolved later. */
    UNKNOWN(false),

    /** We did not call the PSP because its circuit breaker is open. */
    CIRCUIT_OPEN(false);

    private final boolean pspHealthy;

    PspOutcome(boolean pspHealthy) {
        this.pspHealthy = pspHealthy;
    }

    /** Declines count as healthy for the circuit breaker: the PSP did its job. */
    public boolean isPspHealthy() {
        return pspHealthy;
    }
}
