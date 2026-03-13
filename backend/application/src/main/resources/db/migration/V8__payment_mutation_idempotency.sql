CREATE TABLE IF NOT EXISTS payment_mutations (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    mutation_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(128) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider_reference VARCHAR(255),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_mutations_idempotency_key
    ON payment_mutations (idempotency_key);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_mutations_one_pending_per_payment
    ON payment_mutations (payment_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_payment_mutations_payment_type_created_at
    ON payment_mutations (payment_id, mutation_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_mutations_payment_status
    ON payment_mutations (payment_id, status);
