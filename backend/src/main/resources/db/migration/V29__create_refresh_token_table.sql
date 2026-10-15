-- Session longue sur l'appareil (docs/decisions.md n° 54) : le jeton d'accès reste
-- court (JWT, 60 min) ; un jeton de renouvellement, valable 30 jours et prolongé à
-- chaque utilisation, permet d'en obtenir un nouveau sans se reconnecter.
-- Seule l'empreinte SHA-256 du jeton est stockée. La déconnexion le révoque.
CREATE TABLE refresh_token (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
