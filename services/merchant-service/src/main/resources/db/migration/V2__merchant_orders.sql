CREATE TABLE merchant.merchant_orders (
    id UUID PRIMARY KEY, merchant_id UUID NOT NULL, parcel_id UUID NOT NULL UNIQUE,
    tracking_no VARCHAR(32) NOT NULL UNIQUE, order_ref VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL, fee_lkr NUMERIC(10,2) NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT -1,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ON merchant.merchant_orders (merchant_id, created_at DESC);
CREATE TABLE merchant.idempotency_keys (
    merchant_id UUID NOT NULL, key_hash VARCHAR(64) NOT NULL, request_hash VARCHAR(64) NOT NULL,
    response_body TEXT NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (merchant_id, key_hash)
);
CREATE TABLE merchant.order_history (
    id UUID PRIMARY KEY, parcel_id UUID NOT NULL REFERENCES merchant.merchant_orders(parcel_id),
    event_id UUID UNIQUE, from_status VARCHAR(30), to_status VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ON merchant.order_history (parcel_id, occurred_at);
