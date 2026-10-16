-- Notifications par e-mail et SMS (docs/decisions.md n° 58) : pour chaque type de
-- notification, le super administrateur active l'e-mail et/ou le SMS. Un type absent
-- de la table n'est envoyé que dans l'application (et en push).
CREATE TABLE notification_channel (
    type           VARCHAR(50) PRIMARY KEY,
    email_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    sms_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);
