-- V8: Expand new_merchant_behavior for REQUIRE_APPROVAL, track purchase_mode,
-- UserProfile sandbox_wallet_balance, and PaymentAttempt capture metadata.

ALTER TABLE mandates
    ALTER COLUMN new_merchant_behavior TYPE VARCHAR(32);

ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS sandbox_wallet_balance NUMERIC(12, 2) DEFAULT 5000.00;

UPDATE user_profiles
SET sandbox_wallet_balance = 5000.00
WHERE sandbox_wallet_balance IS NULL;

ALTER TABLE purchase_intents
    ADD COLUMN IF NOT EXISTS purchase_mode VARCHAR(32) DEFAULT 'AI_AGENT';

UPDATE purchase_intents
SET purchase_mode = 'AI_AGENT'
WHERE purchase_mode IS NULL;

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS amount NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS paypal_payer_id VARCHAR(128);

