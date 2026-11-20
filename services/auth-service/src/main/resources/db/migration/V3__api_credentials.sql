-- Merchant API credentials (M2). An API_KEY is sent as X-API-Key and exchanged for a JWT at the gateway;
-- a CLIENT is an OAuth 2.0 client-credentials pair whose client_id is key_prefix.
-- Only the SHA-256 of the secret is stored. Revoking sets revoked_at; rows are never deleted.
CREATE TABLE api_credentials (
    id           UUID PRIMARY KEY,
    merchant_id  UUID         NOT NULL REFERENCES merchant_accounts (id) ON DELETE CASCADE,
    type         VARCHAR(20)  NOT NULL CHECK (type IN ('API_KEY', 'CLIENT')),
    name         VARCHAR(80)  NOT NULL,
    key_prefix   VARCHAR(40)  NOT NULL,   -- visible part shown in the UI (API_KEY) or the client_id (CLIENT)
    secret_hash  VARCHAR(64)  NOT NULL UNIQUE,
    scopes       VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX idx_api_credentials_merchant ON api_credentials (merchant_id);
CREATE UNIQUE INDEX uq_api_credentials_client_id ON api_credentials (key_prefix) WHERE type = 'CLIENT';
