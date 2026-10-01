CREATE TABLE payments (
    id              VARCHAR(40)  PRIMARY KEY,
    merchant_id     VARCHAR(64)  NOT NULL,
    idempotency_key VARCHAR(64)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    amount_minor    BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency        VARCHAR(3)   NOT NULL,
    method          VARCHAR(16)  NOT NULL,
    payer           VARCHAR(100) NOT NULL,
    description     VARCHAR(255),
    status          VARCHAR(16)  NOT NULL,
    psp             VARCHAR(32),
    psp_reference   VARCHAR(64),
    failure_code    VARCHAR(64),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    -- The real duplicate-payment guarantee. Redis only makes concurrent duplicates fail faster.
    CONSTRAINT uq_payments_merchant_idempotency_key UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at DESC);
CREATE INDEX idx_payments_created ON payments (created_at);
-- Partial index: the sweeper only ever looks at PROCESSING rows, which are a tiny fraction.
CREATE INDEX idx_payments_processing ON payments (updated_at) WHERE status = 'PROCESSING';

CREATE TABLE payment_attempts (
    id          BIGSERIAL   PRIMARY KEY,
    payment_id  VARCHAR(40) NOT NULL REFERENCES payments (id),
    psp         VARCHAR(32) NOT NULL,
    outcome     VARCHAR(16) NOT NULL,
    code        VARCHAR(64),
    latency_ms  BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_attempts_payment ON payment_attempts (payment_id);

CREATE TABLE outbox_events (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     VARCHAR(40)  NOT NULL UNIQUE,
    aggregate_id VARCHAR(40)  NOT NULL,
    event_type   VARCHAR(40)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_id);
