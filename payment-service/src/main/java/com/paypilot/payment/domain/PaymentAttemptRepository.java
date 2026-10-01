package com.paypilot.payment.domain;

import java.util.List;

import com.paypilot.payment.psp.PspOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByPaymentIdOrderByIdAsc(String paymentId);

    List<PaymentAttempt> findTop50ByOrderByIdDesc();

    @Query("""
            select a.psp as psp, a.outcome as outcome, count(a) as count,
                   coalesce(avg(a.latencyMs), 0) as avgLatency
            from PaymentAttempt a
            group by a.psp, a.outcome
            """)
    List<PspOutcomeTotal> totalsByPspAndOutcome();

    interface PspOutcomeTotal {
        String getPsp();

        PspOutcome getOutcome();

        Long getCount();

        Double getAvgLatency();
    }
}
