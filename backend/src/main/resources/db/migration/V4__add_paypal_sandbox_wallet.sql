-- V4: Track Personal Sandbox buyer wallet balance ($5,000.00 default) and buyer identity
ALTER TABLE mandates
    ADD COLUMN IF NOT EXISTS sandbox_balance NUMERIC(12, 2) DEFAULT 5000.00,
    ADD COLUMN IF NOT EXISTS paypal_payer_name VARCHAR(255);

UPDATE mandates
SET sandbox_balance = 5000.00
WHERE sandbox_balance IS NULL;

