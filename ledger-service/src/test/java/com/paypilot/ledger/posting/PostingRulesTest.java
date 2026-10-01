package com.paypilot.ledger.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.paypilot.common.events.PaymentEvent;
import com.paypilot.common.events.PaymentEventType;
import com.paypilot.ledger.domain.Account;
import com.paypilot.ledger.domain.Direction;
import com.paypilot.ledger.domain.EntryType;
import com.paypilot.ledger.domain.JournalEntry;
import org.junit.jupiter.api.Test;

class PostingRulesTest {

    private final PostingRules rules = new PostingRules(200);

    @Test
    void captureSplitsAmountBetweenMerchantAndPlatformFee() {
        List<Posting> postings = rules.forCapture(succeeded(49_900));

        assertThat(postings).containsExactly(
                Posting.debit("psp_receivable:alpha", com.paypilot.ledger.domain.AccountType.ASSET, 49_900),
                Posting.credit("merchant_payable:m_demo", com.paypilot.ledger.domain.AccountType.LIABILITY, 48_902),
                Posting.credit("platform_fee_revenue", com.paypilot.ledger.domain.AccountType.REVENUE, 998));
        assertThat(sum(postings, Direction.DEBIT)).isEqualTo(sum(postings, Direction.CREDIT));
    }

    @Test
    void feeRoundsHalfUpToTheNearestPaisa() {
        assertThat(rules.feeFor(25)).isEqualTo(1);   // 0.5 paise -> 1
        assertThat(rules.feeFor(24)).isEqualTo(0);   // 0.48 paise -> 0
        assertThat(rules.feeFor(100)).isEqualTo(2);
    }

    @Test
    void zeroFeeProducesTwoPostingsOnly() {
        List<Posting> postings = new PostingRules(0).forCapture(succeeded(10_000));

        assertThat(postings).hasSize(2);
        assertThat(sum(postings, Direction.DEBIT)).isEqualTo(sum(postings, Direction.CREDIT));
    }

    @Test
    void refundMirrorsEveryCaptureLine() {
        List<Posting> capturePostings = rules.forCapture(succeeded(49_900));
        JournalEntry capture = JournalEntry.create("evt_1", "pay_1", EntryType.PAYMENT_CAPTURE, "test",
                capturePostings.stream()
                        .map(p -> new JournalEntry.Line(Account.of(p.accountCode(), p.accountType(), "INR"),
                                p.direction(), p.amountMinor()))
                        .toList());

        List<Posting> reversal = PostingRules.reversalOf(capture);

        assertThat(reversal).hasSize(3);
        for (int i = 0; i < reversal.size(); i++) {
            assertThat(reversal.get(i).accountCode()).isEqualTo(capturePostings.get(i).accountCode());
            assertThat(reversal.get(i).amountMinor()).isEqualTo(capturePostings.get(i).amountMinor());
            assertThat(reversal.get(i).direction()).isEqualTo(capturePostings.get(i).direction().opposite());
        }
    }

    @Test
    void rejectsImpossibleFee() {
        assertThatThrownBy(() -> new PostingRules(10_001)).isInstanceOf(IllegalArgumentException.class);
    }

    private static PaymentEvent succeeded(long amount) {
        return new PaymentEvent("evt_x", PaymentEventType.PAYMENT_SUCCEEDED, "pay_x", "m_demo", amount, "INR",
                "alpha", null, Instant.now());
    }

    private static long sum(List<Posting> postings, Direction direction) {
        return postings.stream().filter(p -> p.direction() == direction).mapToLong(Posting::amountMinor).sum();
    }
}
