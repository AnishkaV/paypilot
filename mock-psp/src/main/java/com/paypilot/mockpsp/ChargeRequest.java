package com.paypilot.mockpsp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * @param reference the caller's id for this charge (PayPilot sends its payment id). Sending the
 *                  same reference twice returns the original charge instead of charging again.
 */
public record ChargeRequest(
        @NotBlank String reference,
        @Positive long amountMinor,
        @NotBlank String currency,
        @NotBlank String payer) {
}
