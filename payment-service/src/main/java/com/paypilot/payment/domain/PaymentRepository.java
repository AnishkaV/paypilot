package com.paypilot.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, String>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    List<Payment> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, Instant cutoff, Pageable pageable);

    List<Payment> findTop5000ByCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    @Query("""
            select p.status as status, count(p) as count, coalesce(sum(p.amountMinor), 0) as amount
            from Payment p
            group by p.status
            """)
    List<StatusTotal> totalsByStatus();

    /** Spring Data fills this interface from the query's column aliases. */
    interface StatusTotal {
        PaymentStatus getStatus();

        Long getCount();

        Long getAmount();
    }
}
