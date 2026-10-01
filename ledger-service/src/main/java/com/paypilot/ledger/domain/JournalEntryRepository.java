package com.paypilot.ledger.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    boolean existsByEventId(String eventId);

    boolean existsByPaymentIdAndEntryType(String paymentId, EntryType entryType);

    Optional<JournalEntry> findByPaymentIdAndEntryType(String paymentId, EntryType entryType);
}
