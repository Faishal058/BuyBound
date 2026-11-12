-- V5: User profile & authentication state for personalized BuyBound shopping sessions
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(60),
    password_hash VARCHAR(255),
    shipping_street VARCHAR(255),
    shipping_city VARCHAR(120),
    shipping_state VARCHAR(120),
    shipping_postal_code VARCHAR(40),
    shipping_country VARCHAR(100) DEFAULT 'United States',
    notify_price_drops BOOLEAN NOT NULL DEFAULT TRUE,
    notify_held_sms BOOLEAN NOT NULL DEFAULT TRUE,
    notify_receipt_emails BOOLEAN NOT NULL DEFAULT TRUE,
    paypal_email VARCHAR(255),
    paypal_payer_id VARCHAR(128),
    logged_in BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

