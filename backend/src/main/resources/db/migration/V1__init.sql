-- BuyBound initial schema (PostgreSQL). Managed by Flyway; JPA uses ddl-auto=validate.

CREATE TABLE watches (
    id UUID PRIMARY KEY,
    product_name VARCHAR(200) NOT NULL,
    product_id VARCHAR(200),
    sku VARCHAR(200),
    variant VARCHAR(200),
    merchant_id VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    target_price NUMERIC(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE mandates (
    id UUID PRIMARY KEY,
    version INTEGER NOT NULL,
    watch_id UUID NOT NULL REFERENCES watches(id),
    currency CHAR(3) NOT NULL,
    max_unit_price NUMERIC(19, 4) NOT NULL,
    max_total NUMERIC(19, 4) NOT NULL,
    max_quantity INTEGER NOT NULL,
    max_purchases_per_day INTEGER NOT NULL,
    offer_max_age_seconds BIGINT NOT NULL,
    new_merchant_behavior VARCHAR(10) NOT NULL,
    require_human_approval_above NUMERIC(19, 4),
    max_weekly_spend NUMERIC(19, 4),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_mandate_watch_version UNIQUE (watch_id, version)
);
CREATE INDEX idx_mandates_watch_status ON mandates(watch_id, status);

CREATE TABLE mandate_allowed_merchants (
    mandate_id UUID NOT NULL REFERENCES mandates(id) ON DELETE CASCADE,
    merchant_id VARCHAR(200) NOT NULL,
    PRIMARY KEY (mandate_id, merchant_id)
);

CREATE TABLE purchase_intents (
    id UUID PRIMARY KEY,
    watch_id UUID NOT NULL REFERENCES watches(id),
    mandate_id UUID NOT NULL REFERENCES mandates(id),
    mandate_version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    merchant_id VARCHAR(200) NOT NULL,
    canonical_sku VARCHAR(200),
    variant VARCHAR(200),
    quantity INTEGER NOT NULL,
    currency CHAR(3) NOT NULL,
    max_total NUMERIC(19, 4) NOT NULL,
    guardian_decision_id UUID,
    policy_version VARCHAR(50),
    evidence_hash VARCHAR(200),
    idempotency_key VARCHAR(200) NOT NULL,
    provider_order_id VARCHAR(200),
    failure_reason VARCHAR(500),
    -- embedded offer snapshot
    offer_merchant_id VARCHAR(200),
    offer_sku VARCHAR(200),
    offer_variant VARCHAR(200),
    offer_quantity INTEGER,
    offer_currency CHAR(3),
    offer_unit_price NUMERIC(19, 4),
    offer_total_price NUMERIC(19, 4),
    offer_observed_at TIMESTAMPTZ,
    offer_source VARCHAR(100),
    offer_evidence_hash VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_intent_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_intents_watch_created ON purchase_intents(watch_id, created_at);

CREATE TABLE guardian_decisions (
    id UUID PRIMARY KEY,
    intent_id UUID REFERENCES purchase_intents(id),
    mandate_id UUID NOT NULL REFERENCES mandates(id),
    mandate_version INTEGER NOT NULL,
    decision VARCHAR(10) NOT NULL,
    reason_codes TEXT NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    evidence_hash VARCHAR(200) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_decisions_intent ON guardian_decisions(intent_id);

CREATE TABLE spend_reservations (
    id UUID PRIMARY KEY,
    intent_id UUID NOT NULL REFERENCES purchase_intents(id),
    watch_id UUID NOT NULL REFERENCES watches(id),
    amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_reservation_intent UNIQUE (intent_id)
);
CREATE INDEX idx_reservations_watch_created ON spend_reservations(watch_id, created_at);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    intent_id UUID NOT NULL REFERENCES purchase_intents(id),
    adapter VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(200),
    simulated BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE provider_webhook_events (
    id UUID PRIMARY KEY,
    provider_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_webhook_event UNIQUE (provider_event_id)
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(200),
    correlation_id VARCHAR(200) NOT NULL,
    reason_codes TEXT,
    policy_version VARCHAR(50),
    summary VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_occurred ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_correlation ON audit_events(correlation_id);
