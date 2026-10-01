package com.paypilot.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import reactor.core.publisher.Mono;

/**
 * Token bucket per (route, merchant), kept in this JVM. Same algorithm as Spring Cloud Gateway's
 * RedisRateLimiter, which the infra profile uses so the limit is shared across gateway instances.
 *
 * <p>A bucket holds up to {@code burstCapacity} tokens and refills at {@code replenishRate} tokens
 * per second. Each request takes one token; no token means 429 Too Many Requests.
 */
public class InMemoryRateLimiter implements RateLimiter<Object> {

    private final int replenishRate;
    private final int burstCapacity;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(int replenishRate, int burstCapacity) {
        this.replenishRate = replenishRate;
        this.burstCapacity = burstCapacity;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        long now = System.nanoTime();
        boolean[] allowed = new boolean[1];
        // compute() is atomic per key, so two requests can never spend the same token.
        Bucket bucket = buckets.compute(routeId + ":" + id, (key, current) -> {
            double tokens = current == null
                    ? burstCapacity
                    : Math.min(burstCapacity, current.tokens() + (now - current.updatedNanos()) / 1e9 * replenishRate);
            if (tokens >= 1) {
                tokens -= 1;
                allowed[0] = true;
            }
            return new Bucket(tokens, now);
        });
        Map<String, String> headers = Map.of(
                "X-RateLimit-Remaining", String.valueOf((long) bucket.tokens()),
                "X-RateLimit-Replenish-Rate", String.valueOf(replenishRate),
                "X-RateLimit-Burst-Capacity", String.valueOf(burstCapacity));
        return Mono.just(new Response(allowed[0], headers));
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of();
    }

    @Override
    public Class<Object> getConfigClass() {
        return Object.class;
    }

    @Override
    public Object newConfig() {
        return new Object();
    }

    private record Bucket(double tokens, long updatedNanos) {
    }
}
