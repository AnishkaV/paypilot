package com.paypilot.payment.domain;

/**
 * Payment lifecycle.
 *
 * <pre>
 * CREATED ──► PROCESSING ──► SUCCEEDED ──► REFUNDED
 *    │            │  ▲
 *    │            └──┘ (next PSP, only when the previous one did not process it)
 *    └──────────► FAILED ◄── PROCESSING
 * </pre>
 */
public enum PaymentStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case CREATED -> next == PROCESSING || next == FAILED;
            case PROCESSING -> next == PROCESSING || next == SUCCEEDED || next == FAILED;
            case SUCCEEDED -> next == REFUNDED;
            case FAILED, REFUNDED -> false;
        };
    }
}
