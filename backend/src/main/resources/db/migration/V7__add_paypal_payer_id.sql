-- V7: Track verified PayPal Sandbox Payer ID separately from masked buyer email
ALTER TABLE mandates
    ADD COLUMN IF NOT EXISTS paypal_payer_id VARCHAR(128);

