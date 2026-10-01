package com.paypilot.payment.idempotency;

/** Use with try-with-resources so the lock is released even when processing throws. */
@FunctionalInterface
public interface IdempotencyLock extends AutoCloseable {

    IdempotencyLock NONE = () -> { };

    @Override
    void close();
}
