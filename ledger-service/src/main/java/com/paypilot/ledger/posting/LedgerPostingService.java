package com.paypilot.ledger.posting;

import java.util.List;
import java.util.Optional;

import com.paypilot.common.events.PaymentEvent;
import com.paypilot.ledger.domain.Account;
import com.paypilot.ledger.domain.AccountRepository;
import com.paypilot.ledger.domain.EntryType;
import com.paypilot.ledger.domain.JournalEntry;
import com.paypilot.ledger.domain.JournalEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer: Kafka delivers at least once, so the same event can arrive twice. We
 * check the event id (and the payment + entry type) before posting, and unique constraints back
 * that check up if two deliveries race. The result is exactly-once effect on the ledger.
 */
@Service
public class LedgerPostingService {

    private static final Logger log = LoggerFactory.getLogger(LedgerPostingService.class);

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;
    private final AccountProvisioner provisioner;
    private final PostingRules rules;

    public LedgerPostingService(JournalEntryRepository entries, AccountRepository accounts,
                                AccountProvisioner provisioner, PostingRules rules) {
        this.entries = entries;
        this.accounts = accounts;
        this.provisioner = provisioner;
        this.rules = rules;
    }

    @Transactional
    public Optional<JournalEntry> handle(PaymentEvent event) {
        EntryType type = switch (event.type()) {
            case PAYMENT_SUCCEEDED -> EntryType.PAYMENT_CAPTURE;
            case PAYMENT_REFUNDED -> EntryType.PAYMENT_REFUND;
            case PAYMENT_CREATED, PAYMENT_FAILED -> null; // no money moved
        };
        if (type == null) {
            return Optional.empty();
        }
        if (entries.existsByEventId(event.eventId())
                || entries.existsByPaymentIdAndEntryType(event.paymentId(), type)) {
            log.info("Duplicate {} for payment {} ignored", event.type(), event.paymentId());
            return Optional.empty();
        }

        List<Posting> postings = type == EntryType.PAYMENT_CAPTURE
                ? rules.forCapture(event)
                : PostingRules.reversalOf(entries.findByPaymentIdAndEntryType(event.paymentId(), EntryType.PAYMENT_CAPTURE)
                        .orElseThrow(() -> new MissingCaptureException(event.paymentId())));

        List<JournalEntry.Line> lines = postings.stream()
                .map(p -> new JournalEntry.Line(account(p, event.currency()), p.direction(), p.amountMinor()))
                .toList();
        JournalEntry entry = JournalEntry.create(event.eventId(), event.paymentId(), type,
                type + " " + event.paymentId() + " via " + event.psp(), lines);
        return Optional.of(entries.save(entry));
    }

    private Account account(Posting posting, String currency) {
        return accounts.findByCode(posting.accountCode()).orElseGet(() -> {
            provisioner.createIfMissing(posting.accountCode(), posting.accountType(), currency);
            return accounts.findByCode(posting.accountCode()).orElseThrow();
        });
    }

    /** A refund arrived before its capture. Retrying usually fixes it; if not, it goes to the DLT. */
    public static class MissingCaptureException extends RuntimeException {
        MissingCaptureException(String paymentId) {
            super("No capture entry found for payment " + paymentId);
        }
    }
}
