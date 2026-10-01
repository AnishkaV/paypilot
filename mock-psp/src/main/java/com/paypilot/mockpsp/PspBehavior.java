package com.paypilot.mockpsp;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * How a simulated PSP behaves. Change it at runtime through {@code PUT /admin/psps/{psp}} to
 * rehearse outages.
 *
 * @param down        every call fails with 503 and nothing is charged
 * @param declineRate share of charges declined for business reasons (0.0 to 1.0)
 * @param errorRate   share of calls rejected with 503 before any charge happens
 * @param timeoutRate share of charges that DO happen but answer too slowly, so the caller times
 *                    out without knowing the result
 * @param latencyMs   extra delay added to every charge
 */
public record PspBehavior(
        boolean down,
        @DecimalMin("0.0") @DecimalMax("1.0") double declineRate,
        @DecimalMin("0.0") @DecimalMax("1.0") double errorRate,
        @DecimalMin("0.0") @DecimalMax("1.0") double timeoutRate,
        @Min(0) @Max(30_000) long latencyMs) {

    public static PspBehavior healthy() {
        return new PspBehavior(false, 0.0, 0.0, 0.0, 50);
    }
}
