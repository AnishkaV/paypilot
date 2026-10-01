package com.paypilot.payment.domain;

import java.util.UUID;

/** Prefixed ids (pay_..., evt_...) so you can tell what an id refers to just by looking at it. */
public final class Ids {

    private Ids() {
    }

    public static String newPaymentId() {
        return "pay_" + random();
    }

    public static String newEventId() {
        return "evt_" + random();
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
