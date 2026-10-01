package com.paypilot.payment.psp;

/**
 * @param pspReference the PSP's id for the charge, only set when {@code outcome} is SUCCEEDED
 * @param code         decline or error code, null on success
 */
public record PspResult(PspOutcome outcome, String pspReference, String code, long latencyMs) {

    public static PspResult succeeded(String pspReference, long latencyMs) {
        return new PspResult(PspOutcome.SUCCEEDED, pspReference, null, latencyMs);
    }

    public static PspResult declined(String code, long latencyMs) {
        return new PspResult(PspOutcome.DECLINED, null, code, latencyMs);
    }

    public static PspResult notProcessed(String code, long latencyMs) {
        return new PspResult(PspOutcome.NOT_PROCESSED, null, code, latencyMs);
    }

    public static PspResult unknown(String code, long latencyMs) {
        return new PspResult(PspOutcome.UNKNOWN, null, code, latencyMs);
    }

    public static PspResult circuitOpen() {
        return new PspResult(PspOutcome.CIRCUIT_OPEN, null, "CIRCUIT_OPEN", 0);
    }
}
