package com.paypilot.ledger.domain;

public class UnbalancedEntryException extends RuntimeException {

    public UnbalancedEntryException(String message) {
        super(message);
    }
}
