-- Transactional outbox for auth-service events (merchant.approved), relayed to RabbitMQ by the shared
-- common-lib OutboxRelay. Columns match com.swiftdrop.common.outbox.OutboxEvent (same as parcel.outbox_events).
-- payload holds the full EventEnvelope JSON, which is what consumers receive.
CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (published_at, created_at);
