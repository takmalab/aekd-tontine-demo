-- Codes par e-mail (docs/decisions.md n° 55) : confirmer l'adresse à l'inscription,
-- puis se connecter par code e-mail (adresse vérifiée uniquement).
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

CREATE TABLE email_code (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    purpose      VARCHAR(20) NOT NULL CHECK (purpose IN ('VERIFY', 'LOGIN')),
    code_hash    VARCHAR(255) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    attempts     INTEGER NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_email_code_lookup ON email_code (email, purpose, created_at);
