-- V3: Add Channel3 discovery telemetry, AI forensics columns, PayPal Vault/Order IDs,
-- detailed Guardian rule checklist traces, and persisted offer_snapshots matrix.

ALTER TABLE mandates
    ADD COLUMN IF NOT EXISTS paypal_vault_token VARCHAR(128),
    ADD COLUMN IF NOT EXISTS paypal_payer_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revocation_reason VARCHAR(255);

ALTER TABLE watches
    ADD COLUMN IF NOT EXISTS natural_language_prompt TEXT,
    ADD COLUMN IF NOT EXISTS reference_product_url TEXT,
    ADD COLUMN IF NOT EXISTS baseline_market_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS last_hunted_at TIMESTAMPTZ;

ALTER TABLE purchase_intents
    ADD COLUMN IF NOT EXISTS paypal_authorization_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS paypal_capture_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS approval_url TEXT,
    ADD COLUMN IF NOT EXISTS channel3_product_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS product_url TEXT,
    ADD COLUMN IF NOT EXISTS image_url TEXT,
    ADD COLUMN IF NOT EXISTS baseline_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS verified_savings NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS ai_match_confidence NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS ai_exact_sku_match BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS ai_prompt_injection_detected BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_subscription_trap_detected BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_forensics_summary TEXT;

ALTER TABLE guardian_decisions
    ADD COLUMN IF NOT EXISTS rule_checks_json TEXT;

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS paypal_order_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS paypal_authorization_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS paypal_capture_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS payment_intent_type VARCHAR(32);

-- Dedicated Channel3 + AI Forensics + Guardian multi-merchant offer telemetry ledger
CREATE TABLE IF NOT EXISTS offer_snapshots (
    id UUID PRIMARY KEY,
    watch_id UUID REFERENCES watches(id),
    intent_id UUID REFERENCES purchase_intents(id),
    channel3_product_id VARCHAR(128),
    product_title VARCHAR(500) NOT NULL,
    merchant_id VARCHAR(200) NOT NULL,
    canonical_sku VARCHAR(200),
    currency VARCHAR(3) NOT NULL,
    offer_price NUMERIC(12, 2) NOT NULL,
    baseline_price NUMERIC(12, 2),
    verified_savings NUMERIC(12, 2),
    commission_rate NUMERIC(5, 2),
    product_url TEXT,
    image_url TEXT,
    source VARCHAR(100) NOT NULL,
    ai_match_confidence NUMERIC(5, 2),
    ai_exact_sku_match BOOLEAN NOT NULL DEFAULT TRUE,
    ai_prompt_injection_detected BOOLEAN NOT NULL DEFAULT FALSE,
    ai_subscription_trap_detected BOOLEAN NOT NULL DEFAULT FALSE,
    ai_forensics_summary TEXT,
    guardian_decision VARCHAR(10) NOT NULL,
    reason_codes TEXT,
    rule_checks_json TEXT,
    observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_offer_snapshots_watch ON offer_snapshots(watch_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_offer_snapshots_decision ON offer_snapshots(guardian_decision, created_at DESC);

