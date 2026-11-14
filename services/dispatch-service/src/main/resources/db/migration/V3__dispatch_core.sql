CREATE TABLE couriers (
    id UUID PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    home_zone_id UUID,
    vehicle_type VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE parcel_queue (
    parcel_id UUID PRIMARY KEY,
    tracking_no VARCHAR(40) NOT NULL,
    pickup_city VARCHAR(120) NOT NULL,
    dropoff_city VARCHAR(120) NOT NULL,
    pickup_zone_id UUID,
    weight_kg NUMERIC(10, 3) NOT NULL,
    status VARCHAR(40) NOT NULL,
    courier_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX parcel_queue_status_idx ON parcel_queue (status);
CREATE INDEX parcel_queue_courier_idx ON parcel_queue (courier_id);

CREATE TABLE assignments (
    id UUID PRIMARY KEY,
    parcel_id UUID NOT NULL REFERENCES parcel_queue (parcel_id),
    courier_id UUID NOT NULL REFERENCES couriers (id),
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    unassigned_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX assignments_active_parcel_idx
    ON assignments (parcel_id) WHERE unassigned_at IS NULL;

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_events_unpublished_idx ON outbox_events (created_at) WHERE published_at IS NULL;
