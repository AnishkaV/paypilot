package com.paypilot.payment.idempotency;

/**
 * Short-lived lock per (merchant, Idempotency-Key), so two copies of the same request arriving at
 * the same moment are not processed in parallel. The second one gets 409.
 *
 * <p>This is an optimisation, not the guarantee. The unique constraint on
 * payments(merchant_id, idempotency_key) is what really prevents duplicate payments.
 *
 * <ul>
 *   <li>{@link InMemoryIdempotencyGuard}: local profile, correct for a single instance</li>
 *   <li>{@link RedisIdempotencyGuard}: infra profile, shared by every instance</li>
 * </ul>
 */
public interface IdempotencyGuard {

    /**
     * @throws RequestInProgressException when another request with the same key holds the lock
     */
    IdempotencyLock acquire(String merchantId, String idempotencyKey);

    static String lockKey(String merchantId, String idempotencyKey) {
        return "idem:lock:" + merchantId + ":" + idempotencyKey;
    }
}
