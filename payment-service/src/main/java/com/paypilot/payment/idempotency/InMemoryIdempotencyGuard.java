package com.paypilot.payment.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.paypilot.payment.config.PayPilotProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Same contract as the Redis lock, kept in this JVM's memory. Correct for one instance, which is
 * what the local profile runs. Locks expire after the TTL so a crashed request cannot hold one forever.
 */
@Component
@Profile("!infra")
public class InMemoryIdempotencyGuard implements IdempotencyGuard {

    private final Map<String, Lease> leases = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryIdempotencyGuard(PayPilotProperties properties) {
        this.ttl = properties.idempotency().lockTtl();
    }

    @Override
    public IdempotencyLock acquire(String merchantId, String idempotencyKey) {
        String key = IdempotencyGuard.lockKey(merchantId, idempotencyKey);
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        // compute() runs atomically per key: this is our "SET NX EX".
        Lease lease = leases.compute(key, (k, current) ->
                current == null || current.expiresAt().isBefore(now) ? new Lease(token, now.plus(ttl)) : current);
        if (!lease.token().equals(token)) {
            throw new RequestInProgressException(idempotencyKey);
        }
        return () -> leases.remove(key, lease);
    }

    private record Lease(String token, Instant expiresAt) {
    }
}
