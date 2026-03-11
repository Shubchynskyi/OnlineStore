CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    exchange_name VARCHAR(100) NOT NULL,
    routing_key VARCHAR(160) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status_next_attempt
    ON outbox_events (status, next_attempt_at, id);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status_last_attempt
    ON outbox_events (status, last_attempt_at, id);

CREATE INDEX IF NOT EXISTS idx_outbox_events_created_at
    ON outbox_events (created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_events_event_type
    ON outbox_events (event_type);
