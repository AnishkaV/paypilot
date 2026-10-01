# Design decisions

Short records of the main choices and what they cost. Newest at the bottom.

## 1. Money is `long` paise
**Decision:** store and transmit amounts as integer minor units.
**Why:** floating point can't represent most decimal amounts exactly.
**Cost:** every display layer has to divide by 100; multi-currency with other exponents needs a currency table.

## 2. Idempotency: unique constraint first, lock second
**Decision:** a unique `(merchant_id, idempotency_key)` constraint plus a stored request hash is the guarantee; a short-lived lock (memory or Redis) only turns concurrent duplicates into a clean `409`.
**Why:** a lock alone can expire or be lost; the database constraint cannot be bypassed.
**Cost:** one extra read per request to look up the key.

## 3. Idempotency lives in the payment service, not the gateway
**Decision:** the gateway authenticates and rate limits; the payment service handles idempotency.
**Why:** idempotency needs the request body hash and must commit in the same transaction as the payment.
**Cost:** each service that offers idempotent writes implements it.

## 4. Never fail over after a timeout
**Decision:** fail over only on outcomes that prove nothing was charged (connection refused, `503`). A read timeout leaves the payment `PROCESSING`; a sweeper asks the PSP for the result.
**Why:** failing over after a timeout can charge a customer twice. That is worse than a slow payment.
**Cost:** some payments take seconds longer to confirm; the checkout has to show a pending state.

## 5. Circuit breaker driven by hand
**Decision:** call `tryAcquirePermission` / `onSuccess` / `onError` explicitly instead of decorating a lambda.
**Why:** declines are successful calls, and the outcome classification lives in our code, not in an exception type.
**Cost:** a permission must always be released or recorded; the processor handles that.

## 6. No database transaction around network calls
**Decision:** PSP calls happen between short transactions (`PaymentStateWriter`), never inside one.
**Why:** holding a connection for a 2 second call exhausts the pool under load.
**Cost:** more, smaller transactions, and the state must be persisted before the call (`startAttempt`) so a crash still tells us which PSP to ask.

## 7. Transactional outbox with polling
**Decision:** write events to `outbox_events` in the business transaction; poll with `FOR UPDATE SKIP LOCKED`.
**Why:** avoids the dual write between database and broker, with no extra infrastructure.
**Cost:** up to about 500 ms extra latency and polling load. CDC with Debezium is the upgrade path.

## 8. One topic, keyed by payment id
**Decision:** all payment event types go to `paypilot.payments.events`, key = payment id.
**Why:** Kafka only orders within a partition; one key keeps CREATED, SUCCEEDED and REFUNDED of a payment in order.
**Cost:** consumers see every event type and ignore those they don't need.

## 9. Transport behind an interface (local HTTP vs Kafka)
**Decision:** `EventTransport` with HTTP and Kafka adapters, chosen by Spring profile. The same applies to locks and rate limits.
**Why:** the project must run with nothing installed, without a second code path for the business logic.
**Cost:** the HTTP transport has no dead-letter topic; a rejected event is logged and skipped.

## 10. Append-only double-entry ledger
**Decision:** journal entries plus lines; balances computed with SQL; refunds post reversing entries; a Postgres trigger forbids updates and deletes.
**Why:** auditability, and an integrity check that can prove the books balance.
**Cost:** balance queries scan lines. At scale, add a snapshot table updated in the same transaction.

## 11. Accounts created in their own transaction
**Decision:** `AccountProvisioner` creates missing accounts with `REQUIRES_NEW` and ignores a lost race.
**Why:** on Postgres any error aborts the surrounding transaction, so a duplicate-key error must not happen inside the posting transaction. It also works identically on H2, unlike `INSERT ... ON CONFLICT`.
**Cost:** a second connection for the first event of each new merchant or PSP.

## 12. Gateway strips identity headers
**Decision:** the gateway removes any client-sent `X-Merchant-Id`, sets its own from the API key, and removes the key before forwarding.
**Why:** internal services trust that header; a client must never be able to set it.
**Cost:** services must not be exposed directly. In production that means a private network or mTLS.

## 13. Console served by the gateway, hash routing
**Decision:** the React build is copied into the gateway's static resources; routes use `#/path`.
**Why:** one URL, same origin as the API (no CORS), and the gateway needs no SPA fallback.
**Cost:** URLs contain `#`. A dedicated CDN plus history routing is the production option.
