INSERT INTO payment_provider_configs (provider_code, display_name, is_enabled, supported_countries)
VALUES ('paypal', 'PayPal', true, '["US","DE","GB","FR","IT","ES","NL","PL","UA"]'),
       ('card', 'Credit/Debit Card', false, '["US","DE","GB","FR","IT","ES","NL","PL"]'),
       ('bank_transfer', 'Bank Transfer (SEPA)', false, '["DE","FR","IT","ES","NL","AT","BE"]'),
       ('crypto', 'Cryptocurrency', false, '["US","DE","GB"]');

INSERT INTO categories (name, slug, sort_order)
VALUES ('Electronics', 'electronics', 1),
       ('Clothing', 'clothing', 2),
       ('Home & Garden', 'home-garden', 3),
       ('Sports', 'sports', 4);
