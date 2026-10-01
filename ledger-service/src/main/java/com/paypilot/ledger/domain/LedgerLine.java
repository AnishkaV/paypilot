package com.paypilot.ledger.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One side of a journal entry: this account, debited or credited, by this much. */
@Entity
@Table(name = "ledger_lines")
public class LedgerLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id")
    private JournalEntry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private long amountMinor;

    protected LedgerLine() {
        // for JPA
    }

    LedgerLine(JournalEntry entry, Account account, Direction direction, long amountMinor) {
        this.entry = entry;
        this.account = account;
        this.direction = direction;
        this.amountMinor = amountMinor;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }
}
