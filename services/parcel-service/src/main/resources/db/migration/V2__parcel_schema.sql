-- M3 Phase 2 Database Schema Migration for parcel-service
-- Schema: parcel

CREATE TABLE IF NOT EXISTS parcel.zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    boundary_wkt TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS parcel.pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id UUID NOT NULL,
    strategy VARCHAR(50) NOT NULL,
    base_fee NUMERIC(10, 2) NOT NULL DEFAULT 350.00,
    per_kg_fee NUMERIC(10, 2) NOT NULL DEFAULT 80.00,
    size_multiplier NUMERIC(4, 2) NOT NULL DEFAULT 1.00,
    valid_from TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_rules_zone FOREIGN KEY (zone_id) REFERENCES parcel.zones(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS parcel.parcels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tracking_no VARCHAR(32) NOT NULL UNIQUE,
    customer_id UUID,
    merchant_id UUID,
    courier_id UUID,
    pickup_zone_id UUID,
    dropoff_zone_id UUID,
    weight_kg NUMERIC(6, 2) NOT NULL,
    size VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    fee_lkr NUMERIC(10, 2) NOT NULL,
    current_status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    pickup_address TEXT NOT NULL,
    dropoff_address TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_parcels_tracking_no ON parcel.parcels(tracking_no);
CREATE INDEX IF NOT EXISTS idx_parcels_customer_id ON parcel.parcels(customer_id);
CREATE INDEX IF NOT EXISTS idx_parcels_courier_id ON parcel.parcels(courier_id);
CREATE INDEX IF NOT EXISTS idx_parcels_current_status ON parcel.parcels(current_status);

CREATE TABLE IF NOT EXISTS parcel.parcel_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_id UUID,
    note VARCHAR(255),
    proof_url VARCHAR(500),
    failure_reason VARCHAR(255),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_parcel FOREIGN KEY (parcel_id) REFERENCES parcel.parcels(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_history_parcel_id ON parcel.parcel_status_history(parcel_id);
CREATE INDEX IF NOT EXISTS idx_history_occurred_at ON parcel.parcel_status_history(occurred_at);

CREATE TABLE IF NOT EXISTS parcel.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_published_created ON parcel.outbox_events(published_at, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate_id ON parcel.outbox_events(aggregate_id);

-- Seed standard zones and initial pricing rules for demo scenarios
INSERT INTO parcel.zones (id, name, boundary_wkt, active)
VALUES 
    ('11111111-1111-1111-1111-111111111101', 'Colombo Metro', 'POLYGON((79.8 6.9, 79.9 6.9, 79.9 7.0, 79.8 7.0, 79.8 6.9))', true),
    ('11111111-1111-1111-1111-111111111102', 'Kandy Central', 'POLYGON((80.6 7.2, 80.7 7.2, 80.7 7.3, 80.6 7.3, 80.6 7.2))', true),
    ('11111111-1111-1111-1111-111111111103', 'Galle Suburban', 'POLYGON((80.2 6.0, 80.3 6.0, 80.3 6.1, 80.2 6.1, 80.2 6.0))', true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO parcel.pricing_rules (id, zone_id, strategy, base_fee, per_kg_fee, size_multiplier, valid_from)
VALUES 
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'WEIGHT_TIERED', 350.00, 80.00, 1.00, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111102', 'WEIGHT_TIERED', 450.00, 100.00, 1.00, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111103', 'EXPRESS', 500.00, 120.00, 1.50, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
