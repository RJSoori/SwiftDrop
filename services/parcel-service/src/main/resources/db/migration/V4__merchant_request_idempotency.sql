-- A durable result at the parcel boundary prevents duplicates after merchant-service crashes.
CREATE TABLE parcel.merchant_requests (
    merchant_id UUID NOT NULL, key_hash VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL, response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (merchant_id,key_hash)
);
