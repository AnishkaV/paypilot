package com.paypilot.payment.psp;

/**
 * Body of a charge request to a PSP.
 *
 * @param reference our payment id. PSPs treat it as an idempotency key, so a retried request
 *                  can never charge the customer twice at the same PSP.
 */
public record ChargeCommand(String reference, long amountMinor, String currency, String payer) {
}
