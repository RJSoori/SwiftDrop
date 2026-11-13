-- Migration V3: Add contracts fields and idempotency table for Phase 2 M3
ALTER TABLE parcel.parcels
    ADD COLUMN IF NOT EXISTS origin VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN IF NOT EXISTS merchant_order_ref VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pickup_city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pickup_lat NUMERIC(9, 6),
    ADD COLUMN IF NOT EXISTS pickup_lng NUMERIC(9, 6),
    ADD COLUMN IF NOT EXISTS dropoff_city VARCHAR(100),
    ADD COLUMN IF NOT EXISTS dropoff_lat NUMERIC(9, 6),
    ADD COLUMN IF NOT EXISTS dropoff_lng NUMERIC(9, 6),
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS length_cm NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS width_cm NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS height_cm NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS service_type VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS parcel.idempotency_keys (
    key VARCHAR(120) PRIMARY KEY,
    response_body TEXT NOT NULL,
    status_code INT NOT NULL DEFAULT 201,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON parcel.idempotency_keys(created_at);
