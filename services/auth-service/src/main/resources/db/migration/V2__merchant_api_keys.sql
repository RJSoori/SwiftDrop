CREATE TABLE auth.merchant_api_keys (
    id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES auth.users(id),
    key_hash VARCHAR(64) NOT NULL UNIQUE, prefix VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), revoked_at TIMESTAMPTZ
);
CREATE INDEX ON auth.merchant_api_keys (user_id,created_at DESC);
