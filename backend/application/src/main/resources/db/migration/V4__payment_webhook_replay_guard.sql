CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    provider_code VARCHAR(50) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    payment_id BIGINT REFERENCES payments (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_webhook_events_provider_event
    ON payment_webhook_events (provider_code, event_id);

CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_payment_id
    ON payment_webhook_events (payment_id);
