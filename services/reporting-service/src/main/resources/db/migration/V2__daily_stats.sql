-- M8: per-day delivery counts built from parcel events (UC-11). Days are Sri Lankan calendar days (Asia/Colombo).
CREATE TABLE daily_stats (
    day             DATE PRIMARY KEY,
    created_count   INTEGER NOT NULL DEFAULT 0 CHECK (created_count >= 0),
    delivered_count INTEGER NOT NULL DEFAULT 0 CHECK (delivered_count >= 0),
    failed_count    INTEGER NOT NULL DEFAULT 0 CHECK (failed_count >= 0),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotent receiver: an eventId is counted once, however often RabbitMQ delivers it.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
