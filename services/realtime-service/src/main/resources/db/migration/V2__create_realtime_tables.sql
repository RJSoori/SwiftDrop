CREATE TABLE tracking_view (
    tracking_no varchar(64) PRIMARY KEY,
    parcel_id uuid NOT NULL,
    current_status varchar(50) NOT NULL,
    pickup_city varchar(100),
    dropoff_city varchar(100),
    timeline jsonb NOT NULL DEFAULT '[]'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_tracking_view_parcel_id ON tracking_view (parcel_id);

CREATE TABLE processed_events (
    event_id uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);
