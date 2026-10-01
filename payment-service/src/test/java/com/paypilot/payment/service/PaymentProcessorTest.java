package com.paypilot.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.domain.PaymentMethod;
import com.paypilot.payment.psp.ChargeCommand;
import com.paypilot.payment.psp.PspOutcome;
import com.paypilot.payment.psp.PspResult;
import com.paypilot.payment.routing.PspRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The failover rules, tested without a database or network. */
class PaymentProcessorTest {

    private final PspRouter router = mock(PspRouter.class);
    private final PaymentStateWriter writer = mock(PaymentStateWriter.class);
    private final PaymentProcessor processor = new PaymentProcessor(router, writer);
    private final Payment payment = Payment.create("m_test", "key-12345678", "hash", 49_900, "INR",
            PaymentMethod.UPI, "user@upi", null);

    @BeforeEach
    void setUp() {
        when(router.providers()).thenReturn(List.of("alpha", "beta", "gamma"));
        when(router.tryAcquire(anyString())).thenReturn(true);
        when(writer.recordOutcome(anyString(), anyString(), any())).thenReturn(payment);
        when(writer.fail(anyString(), anyString())).thenReturn(payment);
    }

    @Test
    void failsOverToNextPspWhenFirstDidNotProcessTheCharge() {
        chargeAt("alpha", PspResult.notProcessed("PSP_UNAVAILABLE", 5));
        chargeAt("beta", PspResult.succeeded("beta_ref", 40));

        processor.process(payment);

        verify(router).charge(eq("alpha"), any());
        verify(router).charge(eq("beta"), any());
        verify(router, never()).charge(eq("gamma"), any());
        verify(writer, never()).fail(anyString(), anyString());
    }

    @Test
    void neverFailsOverAfterATimeoutBecauseTheCustomerMayAlreadyBeCharged() {
        chargeAt("alpha", PspResult.unknown("PSP_TIMEOUT", 2_000));

        processor.process(payment);

        verify(router, never()).charge(eq("beta"), any());
        verify(writer, never()).fail(anyString(), anyString());
    }

    @Test
    void declineIsFinalAndDoesNotTryOtherPsps() {
        chargeAt("alpha", PspResult.declined("INSUFFICIENT_FUNDS", 30));

        processor.process(payment);

        verify(router, never()).charge(eq("beta"), any());
    }

    @Test
    void skipsPspsWithOpenCircuitWithoutCallingThem() {
        when(router.tryAcquire("alpha")).thenReturn(false);
        chargeAt("beta", PspResult.succeeded("beta_ref", 40));

        processor.process(payment);

        verify(router, never()).charge(eq("alpha"), any());
        verify(writer).recordOutcome(eq(payment.getId()), eq("alpha"),
                org.mockito.ArgumentMatchers.argThat(r -> r.outcome() == PspOutcome.CIRCUIT_OPEN));
        verify(writer, never()).startAttempt(payment.getId(), "alpha");
    }

    @Test
    void failsWithNoPspAvailableWhenEveryPspIsDownOrOpen() {
        when(router.tryAcquire("alpha")).thenReturn(false);
        chargeAt("beta", PspResult.notProcessed("PSP_UNREACHABLE", 3));
        chargeAt("gamma", PspResult.notProcessed("PSP_UNAVAILABLE", 3));

        processor.process(payment);

        verify(writer).fail(payment.getId(), PaymentProcessor.NO_PSP_AVAILABLE);
    }

    @Test
    void returnsWhateverTheWriterRecorded() {
        chargeAt("alpha", PspResult.succeeded("ref", 10));

        assertThat(processor.process(payment)).isSameAs(payment);
    }

    private void chargeAt(String psp, PspResult result) {
        when(router.charge(eq(psp), any(ChargeCommand.class))).thenReturn(result);
    }
}
