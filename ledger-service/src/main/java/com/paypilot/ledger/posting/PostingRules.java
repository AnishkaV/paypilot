package com.paypilot.ledger.posting;

import java.util.ArrayList;
import java.util.List;

import com.paypilot.common.events.PaymentEvent;
import com.paypilot.ledger.domain.AccountType;
import com.paypilot.ledger.domain.JournalEntry;
import com.paypilot.ledger.domain.LedgerLine;

/**
 * Turns payment events into balanced postings. Pure functions, no database, easy to unit test.
 *
 * <p>Example: a Rs 499.00 UPI payment through PSP alpha with a 2% fee
 * <pre>
 *   DEBIT  psp_receivable:alpha       49900   (alpha owes us the full amount)
 *   CREDIT merchant_payable:m_demo    48902   (we owe the merchant amount minus fee)
 *   CREDIT platform_fee_revenue         998   (our fee)
 *                                    ------
 *   debits 49900 == credits 49900
 * </pre>
 */
public class PostingRules {

    private final int feeBps;

    public PostingRules(int feeBps) {
        if (feeBps < 0 || feeBps > 10_000) {
            throw new IllegalArgumentException("feeBps must be between 0 and 10000, got " + feeBps);
        }
        this.feeBps = feeBps;
    }

    public List<Posting> forCapture(PaymentEvent event) {
        long amount = event.amountMinor();
        long fee = feeFor(amount);
        List<Posting> postings = new ArrayList<>();
        postings.add(Posting.debit(AccountCodes.pspReceivable(event.psp()), AccountType.ASSET, amount));
        postings.add(Posting.credit(AccountCodes.merchantPayable(event.merchantId()), AccountType.LIABILITY, amount - fee));
        if (fee > 0) {
            postings.add(Posting.credit(AccountCodes.PLATFORM_FEE_REVENUE, AccountType.REVENUE, fee));
        }
        return postings;
    }

    /**
     * A refund mirrors the original capture line by line. Reversing the stored entry (instead of
     * recalculating) means a later fee change can never make a refund post the wrong amounts.
     */
    public static List<Posting> reversalOf(JournalEntry capture) {
        List<Posting> postings = new ArrayList<>();
        for (LedgerLine line : capture.getLines()) {
            postings.add(new Posting(line.getAccount().getCode(), line.getAccount().getType(),
                    line.getDirection().opposite(), line.getAmountMinor()));
        }
        return postings;
    }

    /** Fee rounded half up to the nearest paisa, using integer maths only. */
    long feeFor(long amountMinor) {
        return (amountMinor * feeBps + 5_000) / 10_000;
    }
}
