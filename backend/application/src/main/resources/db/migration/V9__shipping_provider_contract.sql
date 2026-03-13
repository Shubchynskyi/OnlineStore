CREATE TABLE IF NOT EXISTS shipping_provider_configs (
    id BIGSERIAL PRIMARY KEY,
    provider_code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    supported_countries JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipping_provider_configs_supported_countries_array
        CHECK (jsonb_typeof(supported_countries) = 'array')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_shipping_provider_configs_provider_code
    ON shipping_provider_configs (provider_code);

INSERT INTO shipping_provider_configs (provider_code, display_name, is_enabled, supported_countries)
VALUES ('dhl', 'DHL Europe', true, '["AT","BE","DE","ES","FR","IT","NL","PL"]'),
       ('nova_poshta', 'Nova Poshta', true, '["UA"]'),
       ('stub', 'Stub Shipping', false, '["US","DE","GB","FR","IT","ES","NL","PL","UA"]')
ON CONFLICT (provider_code) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_shipments_provider_code'
    ) THEN
        ALTER TABLE shipments
            ADD CONSTRAINT fk_shipments_provider_code
                FOREIGN KEY (provider_code) REFERENCES shipping_provider_configs (provider_code)
                    ON UPDATE CASCADE
                    ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_shipments_provider_code
    ON shipments (provider_code);
