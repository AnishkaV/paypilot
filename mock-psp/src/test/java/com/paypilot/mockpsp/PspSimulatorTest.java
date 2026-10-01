package com.paypilot.mockpsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PspSimulatorTest {

    private final PspSimulator simulator = new PspSimulator(0);

    @Test
    void sameReferenceReturnsTheOriginalChargeInsteadOfChargingTwice() {
        Charge first = simulator.charge("alpha", request("pay_1", "user@upi"));
        Charge second = simulator.charge("alpha", request("pay_1", "user@upi"));

        assertThat(second.pspReference()).isEqualTo(first.pspReference());
        assertThat(first.status()).isEqualTo(Charge.Status.SUCCEEDED);
    }

    @Test
    void payerContainingDeclineIsDeclined() {
        Charge charge = simulator.charge("beta", request("pay_2", "decline@upi"));

        assertThat(charge.status()).isEqualTo(Charge.Status.DECLINED);
        assertThat(charge.declineCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void downPspRejectsAndChargesNothing() {
        simulator.update("gamma", new PspBehavior(true, 0, 0, 0, 0));

        assertThatThrownBy(() -> simulator.charge("gamma", request("pay_3", "user@upi")))
                .isInstanceOf(PspUnavailableException.class);
        simulator.reset();
        assertThatThrownBy(() -> simulator.get("gamma", "pay_3"))
                .isInstanceOf(ChargeNotFoundException.class);
    }

    @Test
    void refundIsIdempotentAndOnlyAllowedForSucceededCharges() {
        simulator.charge("alpha", request("pay_4", "user@upi"));
        simulator.charge("alpha", request("pay_5", "decline@upi"));

        assertThat(simulator.refund("alpha", "pay_4").status()).isEqualTo(Charge.Status.REFUNDED);
        assertThat(simulator.refund("alpha", "pay_4").status()).isEqualTo(Charge.Status.REFUNDED);
        assertThatThrownBy(() -> simulator.refund("alpha", "pay_5"))
                .isInstanceOf(NotRefundableException.class);
    }

    private static ChargeRequest request(String reference, String payer) {
        return new ChargeRequest(reference, 49_900, "INR", payer);
    }
}
