CREATE TABLE dispatch.delivery_proofs (
    id              UUID PRIMARY KEY,
    parcel_id       UUID         NOT NULL,
    courier_id      UUID         NOT NULL,
    object_key      VARCHAR(1024) NOT NULL,
    recipient_name  VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE dispatch.delivery_otps (
    parcel_id   UUID PRIMARY KEY,
    code_hash   VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    attempts    INTEGER      NOT NULL DEFAULT 0,
    verified_at TIMESTAMPTZ
);
