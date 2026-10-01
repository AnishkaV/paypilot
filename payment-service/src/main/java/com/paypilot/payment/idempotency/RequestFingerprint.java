package com.paypilot.payment.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 of the request fields. Stored with the payment so that a retry with the same
 * Idempotency-Key but a different body (say, a different amount) is rejected instead of
 * silently returning the old payment.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(Object... fields) {
        StringBuilder canonical = new StringBuilder();
        for (Object field : fields) {
            String value = Objects.toString(field, "");
            // Length prefix so ("ab", "c") and ("a", "bc") can never produce the same input.
            canonical.append(value.length()).append(':').append(value).append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
