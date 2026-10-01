package com.paypilot.ledger.messaging;

/** An event that can never be processed, no matter how often we retry. Goes straight to the DLT. */
public class PoisonEventException extends RuntimeException {

    public PoisonEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
