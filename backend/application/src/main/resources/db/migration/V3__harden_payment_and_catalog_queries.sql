CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_payment_binding
    ON payments (provider_code, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_active_attempt_per_order_provider
    ON payments (order_id, provider_code)
    WHERE status IN ('PENDING', 'REQUIRES_ACTION', 'AUTHORIZED', 'PAID');

CREATE INDEX IF NOT EXISTS idx_products_status_category
    ON products (status, category_id);

CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON products USING GIN (lower(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_product_variants_active_price_product
    ON product_variants (price_amount, product_id)
    WHERE is_active = TRUE;
