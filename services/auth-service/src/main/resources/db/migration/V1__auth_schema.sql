-- auth-service owns the "auth" schema. Flyway runs this with search_path = auth.

CREATE TABLE roles (
    name VARCHAR(20) PRIMARY KEY
);
INSERT INTO roles (name) VALUES ('CUSTOMER'), ('MERCHANT'), ('COURIER'), ('DISPATCHER'), ('ADMIN');

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(254) NOT NULL UNIQUE,
    password_hash   VARCHAR(100),               -- null for Google-only accounts
    full_name       VARCHAR(120) NOT NULL,
    phone_encrypted VARCHAR(255),               -- AES-GCM, base64(iv || ciphertext)
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL REFERENCES roles (name),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE oauth_accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    UNIQUE (provider, provider_subject)
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,    -- SHA-256 hex; the raw token is never stored
    family_id   UUID        NOT NULL,           -- every token rotated from one login shares this
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens (id),
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE merchant_accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    business_name    VARCHAR(160) NOT NULL,
    registration_no  VARCHAR(40)  NOT NULL,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    rejection_reason VARCHAR(500),
    reviewed_by      UUID REFERENCES users (id),
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_merchant_accounts_status ON merchant_accounts (status);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(60)  NOT NULL,
    target      VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_id);
