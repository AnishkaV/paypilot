package com.paypilot.ledger.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * A balanced set of ledger lines recorded together. The rule that makes double-entry work:
 * total debits always equal total credits, so money is never created or lost, only moved.
 *
 * <p>Entries are append-only. A mistake is fixed with a new reversing entry, never an UPDATE
 * (a database trigger rejects updates and deletes).
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventId;
    private String paymentId;
    @Enumerated(EnumType.STRING)
    private EntryType entryType;
    private String description;
    private Instant createdAt;

    // mappedBy: the line owns the foreign key, so Hibernate inserts lines with entry_id already set
    // instead of inserting and then UPDATE-ing them (which the append-only trigger would reject).
    @OneToMany(mappedBy = "entry", cascade = CascadeType.PERSIST)
    private List<LedgerLine> lines = new ArrayList<>();

    protected JournalEntry() {
        // for JPA
    }

    /**
     * @throws UnbalancedEntryException if the lines do not balance or are otherwise invalid
     */
    public static JournalEntry create(String eventId, String paymentId, EntryType type,
                                      String description, List<Line> lines) {
        validate(lines);
        JournalEntry entry = new JournalEntry();
        entry.eventId = eventId;
        entry.paymentId = paymentId;
        entry.entryType = type;
        entry.description = description;
        entry.createdAt = Instant.now();
        for (Line line : lines) {
            entry.lines.add(new LedgerLine(entry, line.account(), line.direction(), line.amountMinor()));
        }
        return entry;
    }

    private static void validate(List<Line> lines) {
        if (lines.size() < 2) {
            throw new UnbalancedEntryException("An entry needs at least two lines, got " + lines.size());
        }
        long debits = 0;
        long credits = 0;
        String currency = lines.get(0).account().getCurrency();
        for (Line line : lines) {
            if (line.amountMinor() <= 0) {
                throw new UnbalancedEntryException("Line amounts must be positive, got " + line.amountMinor());
            }
            if (!line.account().getCurrency().equals(currency)) {
                throw new UnbalancedEntryException("All lines of an entry must use the same currency");
            }
            if (line.direction() == Direction.DEBIT) {
                debits = Math.addExact(debits, line.amountMinor());
            } else {
                credits = Math.addExact(credits, line.amountMinor());
            }
        }
        if (debits != credits) {
            throw new UnbalancedEntryException("Debits " + debits + " != credits " + credits);
        }
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<LedgerLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** Input for {@link #create}: one account movement. */
    public record Line(Account account, Direction direction, long amountMinor) {
    }
}
