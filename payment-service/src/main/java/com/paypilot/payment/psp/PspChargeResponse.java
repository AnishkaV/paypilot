package com.paypilot.payment.psp;

/** What a PSP returns for a charge. Status is kept as a String so a new PSP status can't break parsing. */
record PspChargeResponse(
        String pspReference,
        String reference,
        long amountMinor,
        String currency,
        String status,
        String declineCode) {
}
