-- Paramètres système réglés par le super administrateur (docs/decisions.md n° 57).
-- Ligne unique. Une règle vide (NULL) = la valeur de la configuration du serveur.
CREATE TABLE system_settings (
    id                      SMALLINT PRIMARY KEY CHECK (id = 1),
    -- Association (en-tête et pied des PDF)
    association_name        VARCHAR(150) NOT NULL,
    association_short_name  VARCHAR(30) NOT NULL,
    association_address     VARCHAR(255),
    association_phone       VARCHAR(30),
    association_email       VARCHAR(255),
    logo_storage_key        VARCHAR(500),
    logo_content_type       VARCHAR(100),
    -- Sécurité
    session_days            INTEGER CHECK (session_days BETWEEN 1 AND 365),
    otp_ttl_minutes         INTEGER CHECK (otp_ttl_minutes BETWEEN 1 AND 60),
    otp_max_attempts        INTEGER CHECK (otp_max_attempts BETWEEN 1 AND 20),
    otp_resend_seconds      INTEGER CHECK (otp_resend_seconds BETWEEN 0 AND 3600),
    otp_max_per_hour        INTEGER CHECK (otp_max_per_hour BETWEEN 1 AND 100),
    -- Maintenance : seul le super administrateur peut se connecter
    maintenance_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    maintenance_message     VARCHAR(500),
    updated_by              UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at              TIMESTAMPTZ NOT NULL
);

INSERT INTO system_settings (id, association_name, association_short_name, updated_at)
VALUES (1, 'Amicale des Enfants Kamkop de Douala', 'AEKD', now());
