package com.paypilot.payment.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Locks the oldest unpublished rows. SKIP LOCKED lets several service instances publish in
     * parallel: each one grabs a different batch instead of waiting on the same rows.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("limit") int limit);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByAggregateIdOrderByIdAsc(String aggregateId);
}
