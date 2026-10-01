package com.paypilot.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void happyPathCreatedProcessingSucceededRefunded() {
        Payment payment = newPayment();

        payment.startAttempt("alpha");
        payment.succeed("alpha_ref_1");
        payment.refund();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getPsp()).isEqualTo("alpha");
        assertThat(payment.getPspReference()).isEqualTo("alpha_ref_1");
    }

    @Test
    void canMoveToTheNextPspWhileProcessing() {
        Payment payment = newPayment();

        payment.startAttempt("alpha");
        payment.startAttempt("beta");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.getPsp()).isEqualTo("beta");
    }

    @Test
    void failedPaymentCannotBeRefundedOrRetried() {
        Payment payment = newPayment();
        payment.fail("NO_PSP_AVAILABLE");

        assertThatThrownBy(payment::refund).isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> payment.startAttempt("alpha")).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void cannotRefundBeforeSuccess() {
        Payment payment = newPayment();
        payment.startAttempt("alpha");

        assertThatThrownBy(payment::refund)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("PROCESSING to REFUNDED");
    }

    @Test
    void succeededPaymentCannotFailLater() {
        Payment payment = newPayment();
        payment.startAttempt("alpha");
        payment.succeed("ref");

        assertThatThrownBy(() -> payment.fail("LATE_DECLINE")).isInstanceOf(IllegalStateTransitionException.class);
    }

    private static Payment newPayment() {
        return Payment.create("m_test", "key-12345678", "hash", 49_900, "INR", PaymentMethod.UPI,
                "user@upi", "test");
    }
}
