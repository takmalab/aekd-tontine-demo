-- Choix du fournisseur SMS depuis l'écran « Paramètres SMS » (docs/decisions.md n° 48).
-- Ligne unique (id = 1). active_provider vide : le fournisseur de la
-- configuration (app.sms.provider) est utilisé.
CREATE TABLE sms_settings (
    id              SMALLINT PRIMARY KEY CHECK (id = 1),
    active_provider VARCHAR(30),
    fallback_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

INSERT INTO sms_settings (id, active_provider, fallback_enabled, updated_at) VALUES (1, NULL, TRUE, now());
