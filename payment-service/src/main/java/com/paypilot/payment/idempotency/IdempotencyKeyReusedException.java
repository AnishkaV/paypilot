package com.paypilot.payment.idempotency;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency-Key " + idempotencyKey + " was already used with a different request body");
    }
}
