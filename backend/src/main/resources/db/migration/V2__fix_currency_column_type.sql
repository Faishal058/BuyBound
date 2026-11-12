-- Align currency columns from CHAR(3) (bpchar) to VARCHAR(3) to match JPA String mapping
ALTER TABLE watches ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE mandates ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE purchase_intents ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE purchase_intents ALTER COLUMN offer_currency TYPE VARCHAR(3);
