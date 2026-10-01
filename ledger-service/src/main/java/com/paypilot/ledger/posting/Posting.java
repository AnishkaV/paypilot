package com.paypilot.ledger.posting;

import com.paypilot.ledger.domain.AccountType;
import com.paypilot.ledger.domain.Direction;

/** A planned account movement, before accounts are looked up in the database. */
public record Posting(String accountCode, AccountType accountType, Direction direction, long amountMinor) {

    public static Posting debit(String accountCode, AccountType type, long amountMinor) {
        return new Posting(accountCode, type, Direction.DEBIT, amountMinor);
    }

    public static Posting credit(String accountCode, AccountType type, long amountMinor) {
        return new Posting(accountCode, type, Direction.CREDIT, amountMinor);
    }
}
