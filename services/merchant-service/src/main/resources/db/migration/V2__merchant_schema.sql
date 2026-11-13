-- M6 Phase 2 Database Schema Migration for merchant-service
-- Schema: merchant

CREATE TABLE IF NOT EXISTS merchant.merchant_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    parcel_id UUID NOT NULL,
    tracking_no VARCHAR(32) NOT NULL,
    order_ref VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    fee_lkr NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_merchant_orders_merchant_id ON merchant.merchant_orders(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_tracking_no ON merchant.merchant_orders(tracking_no);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_status ON merchant.merchant_orders(status);
CREATE INDEX IF NOT EXISTS idx_merchant_orders_created_at ON merchant.merchant_orders(created_at);

CREATE TABLE IF NOT EXISTS merchant.webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL UNIQUE,
    url VARCHAR(500) NOT NULL,
    secret_encrypted VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhook_endpoints_merchant_id ON merchant.webhook_endpoints(merchant_id);

CREATE TABLE IF NOT EXISTS merchant.webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    attempt INT NOT NULL DEFAULT 1,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    status_code INT,
    last_error TEXT,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_merchant_id ON merchant.webhook_deliveries(merchant_id);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_event_id ON merchant.webhook_deliveries(event_id);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_state ON merchant.webhook_deliveries(state);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_next_retry ON merchant.webhook_deliveries(next_retry_at);

CREATE TABLE IF NOT EXISTS merchant.idempotency_keys (
    key VARCHAR(120) PRIMARY KEY,
    response_body TEXT NOT NULL,
    status_code INT NOT NULL DEFAULT 201,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_merchant_idempotency_created_at ON merchant.idempotency_keys(created_at);

