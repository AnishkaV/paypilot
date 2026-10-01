package com.paypilot.payment.idempotency;

public class RequestInProgressException extends RuntimeException {

    public RequestInProgressException(String idempotencyKey) {
        super("A request with Idempotency-Key " + idempotencyKey + " is already being processed. Retry shortly.");
    }
}
