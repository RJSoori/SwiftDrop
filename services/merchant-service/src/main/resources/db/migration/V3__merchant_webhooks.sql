CREATE TABLE merchant.webhook_endpoints (
 merchant_id UUID PRIMARY KEY, url VARCHAR(2048) NOT NULL, secret_encrypted TEXT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT true, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE merchant.webhook_deliveries (
 id UUID PRIMARY KEY, merchant_id UUID NOT NULL, event_id UUID NOT NULL, payload TEXT NOT NULL,
 endpoint_url VARCHAR(2048) NOT NULL, secret_encrypted TEXT NOT NULL,
 attempt INTEGER NOT NULL DEFAULT 0, state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 status_code INTEGER, last_error VARCHAR(250), next_retry_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), delivered_at TIMESTAMPTZ, dead_lettered_at TIMESTAMPTZ,
 UNIQUE (merchant_id,event_id)
);
CREATE INDEX ON merchant.webhook_deliveries (state,next_retry_at);
CREATE INDEX ON merchant.webhook_deliveries (merchant_id,created_at DESC);
CREATE TABLE merchant.processed_events (event_id UUID PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL DEFAULT now());

-- Status changes may arrive before a timed-out order request is recovered.
CREATE TABLE merchant.webhook_inbox (event_id UUID PRIMARY KEY,payload TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now());
