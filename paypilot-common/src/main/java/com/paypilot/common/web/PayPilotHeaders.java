package com.paypilot.common.web;

public final class PayPilotHeaders {

    /** Merchant's secret key, checked by the gateway. Never forwarded to internal services. */
    public static final String API_KEY = "X-Api-Key";

    /** Set by the gateway after it authenticates the API key. Internal services trust only this. */
    public static final String MERCHANT_ID = "X-Merchant-Id";

    /** Correlation id that follows a request through every service and log line. */
    public static final String REQUEST_ID = "X-Request-Id";

    /** Client-chosen key that makes POST /payments safe to retry. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Returned as "true" when a response is a replay of an earlier request with the same key. */
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private PayPilotHeaders() {
    }
}
