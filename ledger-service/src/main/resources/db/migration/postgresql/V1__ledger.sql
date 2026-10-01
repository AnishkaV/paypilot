CREATE TABLE accounts (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(120) NOT NULL UNIQUE,
    type       VARCHAR(16)  NOT NULL CHECK (type IN ('ASSET', 'LIABILITY', 'REVENUE', 'EXPENSE')),
    currency   VARCHAR(3)   NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE journal_entries (
    id          BIGSERIAL    PRIMARY KEY,
    -- Idempotent consumer: a Kafka event can be delivered twice but only ever posted once.
    event_id    VARCHAR(40)  NOT NULL UNIQUE,
    payment_id  VARCHAR(40)  NOT NULL,
    entry_type  VARCHAR(40)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL,
    -- Business-level guard: one capture and one (full) refund per payment, whatever the event id.
    CONSTRAINT uq_journal_entries_payment_type UNIQUE (payment_id, entry_type)
);

CREATE TABLE ledger_lines (
    id           BIGSERIAL  PRIMARY KEY,
    entry_id     BIGINT     NOT NULL REFERENCES journal_entries (id),
    account_id   BIGINT     NOT NULL REFERENCES accounts (id),
    direction    VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT     NOT NULL CHECK (amount_minor > 0)
);

CREATE INDEX idx_ledger_lines_account ON ledger_lines (account_id);
CREATE INDEX idx_ledger_lines_entry ON ledger_lines (entry_id);

-- Append-only ledger: history is never edited. Corrections are new reversing entries.
CREATE FUNCTION forbid_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger is append-only: % on % is not allowed', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER journal_entries_append_only
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();

CREATE TRIGGER ledger_lines_append_only
    BEFORE UPDATE OR DELETE ON ledger_lines
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();
