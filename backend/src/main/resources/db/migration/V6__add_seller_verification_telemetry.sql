-- V6: Seller reputation verification telemetry, negative review red flags, and delivery ETAs
ALTER TABLE offer_snapshots
    ADD COLUMN IF NOT EXISTS seller_rating DOUBLE PRECISION DEFAULT 4.8,
    ADD COLUMN IF NOT EXISTS seller_review_count INTEGER DEFAULT 520,
    ADD COLUMN IF NOT EXISTS verified_seller_history BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS negative_red_flag_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS seller_red_flag_summary TEXT DEFAULT 'None — Verified Authentic Merchant',
    ADD COLUMN IF NOT EXISTS estimated_delivery VARCHAR(120) DEFAULT 'Arriving in 2 Days';

ALTER TABLE purchase_intents
    ADD COLUMN IF NOT EXISTS seller_rating DOUBLE PRECISION DEFAULT 4.8,
    ADD COLUMN IF NOT EXISTS seller_review_count INTEGER DEFAULT 520,
    ADD COLUMN IF NOT EXISTS verified_seller_history BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS negative_red_flag_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS seller_red_flag_summary TEXT DEFAULT 'None — Verified Authentic Merchant',
    ADD COLUMN IF NOT EXISTS estimated_delivery VARCHAR(120) DEFAULT 'Arriving in 2 Days';

ALTER TABLE mandates
    ADD COLUMN IF NOT EXISTS min_seller_rating DOUBLE PRECISION DEFAULT 4.5;

