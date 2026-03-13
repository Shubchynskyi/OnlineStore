CREATE TABLE product_attributes (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    value JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_product_attributes_product_id ON product_attributes (product_id);
CREATE INDEX idx_product_attributes_value ON product_attributes USING GIN (value);
CREATE UNIQUE INDEX ux_product_attributes_product_name
    ON product_attributes (product_id, lower(name));

ALTER TABLE product_images
    ADD COLUMN object_key VARCHAR(500);

ALTER TABLE product_images
    ALTER COLUMN url TYPE VARCHAR(1024);

CREATE UNIQUE INDEX ux_product_images_object_key
    ON product_images (object_key)
    WHERE object_key IS NOT NULL;
