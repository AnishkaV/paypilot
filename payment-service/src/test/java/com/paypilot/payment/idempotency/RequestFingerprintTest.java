package com.paypilot.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void sameFieldsGiveSameFingerprint() {
        assertThat(RequestFingerprint.of(49_900L, "INR", "UPI", "a@upi", null))
                .isEqualTo(RequestFingerprint.of(49_900L, "INR", "UPI", "a@upi", null));
    }

    @Test
    void differentAmountGivesDifferentFingerprint() {
        assertThat(RequestFingerprint.of(49_900L, "INR", "UPI", "a@upi", null))
                .isNotEqualTo(RequestFingerprint.of(50_000L, "INR", "UPI", "a@upi", null));
    }

    @Test
    void fieldBoundariesMatter() {
        assertThat(RequestFingerprint.of("ab", "c")).isNotEqualTo(RequestFingerprint.of("a", "bc"));
    }

    @Test
    void isSha256Hex() {
        assertThat(RequestFingerprint.of("x")).hasSize(64).matches("[0-9a-f]+");
    }
}
