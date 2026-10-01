package com.paypilot.mockpsp;

class PspUnavailableException extends RuntimeException {
    PspUnavailableException(String psp) {
        super("PSP " + psp + " is unavailable, request was not processed");
    }
}

class UnknownPspException extends RuntimeException {
    UnknownPspException(String psp) {
        super("Unknown PSP: " + psp);
    }
}

class ChargeNotFoundException extends RuntimeException {
    ChargeNotFoundException(String psp, String reference) {
        super("No charge with reference " + reference + " at PSP " + psp);
    }
}

class NotRefundableException extends RuntimeException {
    NotRefundableException(String reference, Charge.Status status) {
        super("Charge " + reference + " is " + status + " and cannot be refunded");
    }
}
