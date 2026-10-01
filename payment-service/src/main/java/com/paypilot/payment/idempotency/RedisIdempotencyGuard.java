package com.paypilot.payment.idempotency;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.paypilot.payment.config.PayPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Distributed lock in Redis: works across many payment-service instances. Fails open when Redis
 * is down, because the database constraint still prevents duplicates.
 */
@Component
@Profile("infra")
public class RedisIdempotencyGuard implements IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyGuard.class);

    /** Delete the key only if it still holds our token, so we never release someone else's lock. */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisIdempotencyGuard(StringRedisTemplate redis, PayPilotProperties properties) {
        this.redis = redis;
        this.ttl = properties.idempotency().lockTtl();
    }

    @Override
    public IdempotencyLock acquire(String merchantId, String idempotencyKey) {
        String key = IdempotencyGuard.lockKey(merchantId, idempotencyKey);
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            // SET key token NX EX ttl: atomic "create if absent, with expiry"
            acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, continuing without idempotency lock: {}", e.getMessage());
            return IdempotencyLock.NONE;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            throw new RequestInProgressException(idempotencyKey);
        }
        return () -> release(key, token);
    }

    private void release(String key, String token) {
        try {
            redis.execute(RELEASE, List.of(key), token);
        } catch (DataAccessException e) {
            log.warn("Could not release idempotency lock {} (it expires on its own): {}", key, e.getMessage());
        }
    }
}
