package com.paypilot.ledger.domain;

/**
 * The normal side is where increases are recorded. An asset grows with debits, a liability
 * or revenue account grows with credits. Balances are reported on the normal side.
 */
public enum AccountType {
    ASSET(Direction.DEBIT),
    LIABILITY(Direction.CREDIT),
    REVENUE(Direction.CREDIT),
    EXPENSE(Direction.DEBIT);

    private final Direction normalSide;

    AccountType(Direction normalSide) {
        this.normalSide = normalSide;
    }

    public Direction normalSide() {
        return normalSide;
    }

    public long balance(long debits, long credits) {
        return normalSide == Direction.DEBIT ? debits - credits : credits - debits;
    }
}
