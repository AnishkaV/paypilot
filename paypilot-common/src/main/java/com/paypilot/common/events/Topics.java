package com.paypilot.common.events;

public final class Topics {

    /**
     * One topic for every payment event type. Keeping CREATED, SUCCEEDED and REFUNDED on the
     * same topic (and keyed by payment id) is what guarantees a consumer sees them in order.
     */
    public static final String PAYMENT_EVENTS = "paypilot.payments.events";

    /** Where the ledger parks events it could not process after retries. */
    public static final String PAYMENT_EVENTS_DLT = PAYMENT_EVENTS + "-dlt";

    public static final int PAYMENT_EVENTS_PARTITIONS = 6;

    private Topics() {
    }
}
