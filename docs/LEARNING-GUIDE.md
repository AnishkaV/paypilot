# Learning guide

Work through this in order. For each concept: read the code it points to, make it happen in the running app, then answer the interview questions out loud without looking. If you can explain every section in your own words, you can defend this project in any backend interview.

Paths are relative to the repository root. `payment-service/.../` means `payment-service/src/main/java/com/paypilot/payment/`.

---

## 1. Money as integers

**What:** Amounts are `long` paise (minor units), never `double`. `0.1 + 0.2` is `0.30000000000000004` in floating point; in payments that rounding error becomes real money.

**Code:** `paypilot-common/.../events/PaymentEvent.java`, `CreatePaymentRequest.java`, and `PostingRules.feeFor()` in the ledger, which rounds the 2% fee half-up using integer maths only.

**Interview questions**
- Why not `double`? Why not `BigDecimal` everywhere either?
- How would you support currencies with 0 or 3 decimal places (JPY, KWD)?

---

## 2. Idempotency keys

**What:** A client sends `Idempotency-Key` with `POST /payments`. If the network drops and the client retries, the server returns the first result instead of charging again. PayPilot stores the key and a SHA-256 hash of the request body with the payment:
1. Key seen before, same body → return the stored payment (`200`, `Idempotent-Replayed: true`).
2. Key seen before, different body → `422`, because the client has a bug.
3. Two identical requests at the same instant → a short lock lets one through, the other gets `409`.
4. The **unique constraint** on `(merchant_id, idempotency_key)` is the real guarantee. The lock is only an optimisation, which is why it may fail open.

**Code:** `PaymentService.create()`, `idempotency/RequestFingerprint.java`, `InMemoryIdempotencyGuard.java`, `RedisIdempotencyGuard.java` (look at the Lua script: why compare-and-delete?), and the `UNIQUE` constraint in `db/migration/*/V1__payments.sql`.

**Try it:** New payment → keep "send twice" ticked.

**Interview questions**
- Where should the idempotency check live: gateway or service? Why?
- Why hash the body instead of storing it?
- Redis is down. Can a customer be double-charged? Walk through it.
- Why must a lock release check its own token before deleting?

---

## 3. State machine

**What:** A payment can only move along allowed transitions: `CREATED → PROCESSING → SUCCEEDED → REFUNDED`, or to `FAILED`. The entity has no setters; every change goes through a method that checks `canTransitionTo`.

**Code:** `domain/PaymentStatus.java`, `domain/Payment.java`, tests in `PaymentTest.java`.

**Interview questions**
- What bugs does this prevent that plain setters would allow?
- Where would you add partial refunds or authorise/capture as two steps?

---

## 4. Optimistic locking

**What:** `@Version` on `Payment`. If the request thread and the sweeper both update the same payment, the second commit fails with `ObjectOptimisticLockingFailureException` instead of silently overwriting the first.

**Code:** `Payment.version`, the catch in `StuckPaymentSweeper.resolve()`, the `409 CONCURRENT_UPDATE` in `ApiExceptionHandler`.

**Interview questions**
- Optimistic vs pessimistic locking: when do you choose each?
- What SQL does Hibernate actually run for a versioned update?

---

## 5. Timeouts and unknown outcomes (the most important idea)

**What:** After calling a PSP there are three cases, not two:
- **It answered** (succeeded or declined) → final.
- **It provably did not process** (connection refused, `503`) → safe to try the next PSP.
- **It timed out** → the customer **may have been charged**. Retrying at another PSP could charge them twice. PayPilot leaves the payment `PROCESSING` and the sweeper later asks the PSP (`GET /charges/{reference}`) what happened.

PSPs also dedupe on our payment id (`reference`), so even a retry at the *same* PSP is safe.

**Code:** `psp/PspOutcome.java`, `psp/PspClient.fromTransportFailure()` (connect timeout vs read timeout), `service/PaymentProcessor.java`, `service/StuckPaymentSweeper.java`, tests in `PaymentProcessorTest.java`.

**Try it:** Checkout with `timeout@upi`: the page waits, then flips to successful once the sweeper checks.

**Interview questions**
- A PSP call times out. What do you do? (Most candidates say "retry". Explain why that's wrong.)
- Why is a connect timeout safe to fail over but a read timeout isn't?
- How long would you keep asking before escalating to a human?

---

## 6. Circuit breakers

**What:** One Resilience4j breaker per PSP. It counts the last 10 calls; if at least 50% failed it **opens** for 20 seconds and payments skip that PSP instantly. Then it goes **half-open**, lets 2 trial calls through, and closes again if they succeed. Declines count as successes, because the PSP did its job.

**Code:** `config/PspConfig.pspCircuitBreakers()`, `routing/PspRouter.java` (why drive the breaker by hand instead of wrapping a lambda?).

**Try it:** Chaos lab → Alpha outage, with the simulator running. Watch alpha go "Circuit open", then half-open after you heal it.

**Interview questions**
- Circuit breaker vs retry vs timeout vs bulkhead: what does each protect against?
- Why must a decline not trip the breaker?

---

## 7. Transactional outbox

**What:** The dual-write problem: you can't update the database *and* publish to Kafka atomically. If you do both, one can fail and the other not. Instead the payment change and an `outbox_events` row are written in **one transaction**. A poller then publishes unpublished rows and marks them published.

- `SELECT ... FOR UPDATE SKIP LOCKED` lets several instances publish without sending the same row twice.
- Publishing is **at-least-once**: a crash after publishing but before marking means a resend. Consumers must dedupe.
- `Propagation.MANDATORY` on `OutboxWriter.append()` makes it impossible to write an event outside a transaction.

**Code:** `outbox/OutboxWriter.java`, `outbox/OutboxPublisher.java` (includes exponential back-off), `outbox/OutboxRepository.lockNextBatch()`, `service/PaymentStateWriter.java`.

**Interview questions**
- Why not publish to Kafka inside the `@Transactional` method?
- Outbox polling vs CDC (Debezium reading the WAL): trade-offs?
- Why are all events of a payment keyed by the payment id?

---

## 8. Ports and adapters

**What:** The outbox publishes through an `EventTransport` interface. Locally it's `HttpEventTransport`; with the `infra` profile it's `KafkaEventTransport`. The same pattern covers the idempotency lock (memory vs Redis) and the rate limiter. Business code never changes when infrastructure does.

**Code:** `outbox/EventTransport.java` and its two implementations, `@Profile("infra")` / `@Profile("!infra")`, `application-local.yml` vs `application-infra.yml`.

**Interview questions**
- How does Spring decide which bean to inject here?
- What would you need to add to publish to AWS SQS instead?

---

## 9. Idempotent consumer and dead letters

**What:** The ledger may receive the same event twice. It checks `event_id` (and `payment_id + entry_type`) before posting, and unique constraints catch the race if two deliveries arrive together. The effect is exactly-once *on the ledger* even though delivery is at-least-once.

In the infra profile, a failing Kafka message is retried 3 times, then parked on a dead-letter topic so it cannot block its partition. Messages that can never succeed (bad JSON) skip the retries.

**Code:** `ledger-service/.../posting/LedgerPostingService.java`, `messaging/EventIngestController.java` (status codes tell the sender what to do), `config/KafkaLedgerConfig.java`.

**Interview questions**
- Kafka "exactly-once semantics": what does it actually guarantee, and why doesn't it cover writes to a database?
- A message fails forever. What happens to the messages behind it without a DLT?

---

## 10. Double-entry bookkeeping

**What:** Every entry has at least two lines and its debits equal its credits. A ₹499 payment through PSP alpha with a 2% fee:

| Account | Debit | Credit |
|---|---|---|
| `psp_receivable:alpha` (asset: alpha owes us) | 499.00 | |
| `merchant_payable:m_demo` (liability: we owe the merchant) | | 489.02 |
| `platform_fee_revenue` (revenue: our fee) | | 9.98 |

A refund posts the mirror image instead of editing history. The ledger is append-only; in Postgres a trigger rejects `UPDATE` and `DELETE`.

**Code:** `ledger-service/.../domain/JournalEntry.java` (validation), `posting/PostingRules.java`, `api/LedgerQueries.integrity()`, `db/migration/postgresql/V1__ledger.sql` (the trigger).

**Interview questions**
- Why store lines and compute balances instead of updating a `balance` column? When would you add a snapshot?
- Why reverse the original entry on refund rather than recalculating the fee?

---

## 11. API gateway

**What:** One entry point that authenticates, rate limits and routes.
- The client's API key becomes a trusted `X-Merchant-Id`. Any `X-Merchant-Id` the client sends is stripped, so nobody can act as another merchant, and the key never reaches internal services.
- Rate limiting is a **token bucket** per merchant: 20 tokens per second, burst 40. Empty bucket → `429`.
- Admin-only routes for the ledger and ops.

**Code:** `api-gateway/.../ApiKeyAuthFilter.java` (note the constant-time comparison), `InMemoryRateLimiter.java`, `RouteConfig.java`, tests in `ApiKeyAuthFilterTest.java`.

**Try it:** Chaos lab → set 15 payments per second and watch "Rate limited (429)".

**Interview questions**
- Token bucket vs leaky bucket vs fixed window vs sliding window?
- Why must rate-limit state be in Redis once you run two gateway instances?
- What would you replace API keys with for a dashboard used by people? (OAuth2 / OIDC, JWT.)

---

## 12. Database per service and migrations

**What:** Each service owns its schema. Flyway applies versioned SQL at startup (`V1__...sql`). `{vendor}` in the Flyway location picks H2 or Postgres SQL. Hibernate only validates the schema in the infra profile; it never creates it.

**Code:** `db/migration/h2/`, `db/migration/postgresql/`, `spring.flyway.locations` in `application.yml`. Compare the two SQL files: partial indexes and the trigger are Postgres-only.

**Interview questions**
- Why not `ddl-auto=update` in production?
- How do you change a column without downtime (expand and contract)?

---

## 13. Testing strategy

- **Unit tests** with Mockito for rules that must never break: `PaymentProcessorTest` (failover), `PostingRulesTest`, `JournalEntryTest`.
- **Spring Boot tests** on H2 through MockMvc: `PaymentFlowTest`, `LedgerIngestTest`.
- **Testcontainers** for the real infrastructure: `PaymentApiIT`, `LedgerFlowIT` (need Docker, run with `mvn verify -Pit`).
- **End-to-end smoke test** across running services: `scripts/smoke-test.ps1`.

**Interview question:** what does each layer catch that the others miss?

---

## 14. The frontend

React 19 + TypeScript + Vite, TanStack Query for polling and caching, Tailwind CSS, Recharts. Worth reading:
- `frontend/src/lib/api.ts`: typed client that turns problem responses into errors.
- `frontend/src/lib/simulator.ts`: a store outside React (`useSyncExternalStore`) so the traffic generator keeps running across pages.
- `frontend/src/pages/Checkout.tsx`: keeps one idempotency key per order, so a double click can't double charge.

---

## What to learn next (maps to the roadmap)

| Next | Why |
|---|---|
| Docker and Docker Compose | Run the infra profile daily; required for Phase 2 |
| Kafka Streams | Windowed velocity checks for the risk engine |
| Spring AI (ChatClient, tool calling, advisors, VectorStore) | The ops copilot in Phase 3 |
| RAG basics and pgvector | Copilot answers grounded in runbooks |
| MCP (Model Context Protocol) | Expose the ops tools to Claude and IDEs |
| OpenTelemetry, Prometheus, Grafana | Tracing and dashboards in Phase 4 |
| k6 or Gatling | Load test numbers you can put on your resume honestly |
| Kubernetes (Helm, probes, HPA) and AWS | Phase 5 deployment |
