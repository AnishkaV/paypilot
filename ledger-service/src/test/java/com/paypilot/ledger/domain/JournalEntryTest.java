package com.paypilot.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private final Account receivable = Account.of("psp_receivable:alpha", AccountType.ASSET, "INR");
    private final Account payable = Account.of("merchant_payable:m_1", AccountType.LIABILITY, "INR");
    private final Account revenue = Account.of("platform_fee_revenue", AccountType.REVENUE, "INR");

    @Test
    void acceptsBalancedEntry() {
        JournalEntry entry = JournalEntry.create("evt_1", "pay_1", EntryType.PAYMENT_CAPTURE, "test", List.of(
                new JournalEntry.Line(receivable, Direction.DEBIT, 1_000),
                new JournalEntry.Line(payable, Direction.CREDIT, 980),
                new JournalEntry.Line(revenue, Direction.CREDIT, 20)));

        assertThat(entry.getLines()).hasSize(3);
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> JournalEntry.create("evt_2", "pay_2", EntryType.PAYMENT_CAPTURE, "test", List.of(
                new JournalEntry.Line(receivable, Direction.DEBIT, 1_000),
                new JournalEntry.Line(payable, Direction.CREDIT, 999))))
                .isInstanceOf(UnbalancedEntryException.class)
                .hasMessageContaining("1000 != credits 999");
    }

    @Test
    void rejectsSingleLineEntry() {
        assertThatThrownBy(() -> JournalEntry.create("evt_3", "pay_3", EntryType.PAYMENT_CAPTURE, "test", List.of(
                new JournalEntry.Line(receivable, Direction.DEBIT, 1_000))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void rejectsZeroOrNegativeAmounts() {
        assertThatThrownBy(() -> JournalEntry.create("evt_4", "pay_4", EntryType.PAYMENT_CAPTURE, "test", List.of(
                new JournalEntry.Line(receivable, Direction.DEBIT, 0),
                new JournalEntry.Line(payable, Direction.CREDIT, 0))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void rejectsMixedCurrencies() {
        Account usd = Account.of("merchant_payable:m_2", AccountType.LIABILITY, "USD");
        assertThatThrownBy(() -> JournalEntry.create("evt_5", "pay_5", EntryType.PAYMENT_CAPTURE, "test", List.of(
                new JournalEntry.Line(receivable, Direction.DEBIT, 100),
                new JournalEntry.Line(usd, Direction.CREDIT, 100))))
                .isInstanceOf(UnbalancedEntryException.class);
    }
}
