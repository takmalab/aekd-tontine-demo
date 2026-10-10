-- Notifications dans l'application et Web Push (docs/decisions.md n° 44).

CREATE TABLE notification (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    -- Route de l'application à ouvrir (ex. /payments), facultative.
    link VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification (user_id) WHERE read_at IS NULL;

-- Abonnements Web Push : un par navigateur / appareil.
CREATE TABLE push_subscription (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL UNIQUE,
    p256dh VARCHAR(255) NOT NULL,
    auth VARCHAR(255) NOT NULL,
    user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_push_subscription_user ON push_subscription (user_id);

-- Réglages globaux des notifications (une seule ligne, id = 1).
-- reminder_days_before : rappels avant la date limite des cotisations obligatoires ;
-- NULL = pas de rappel. vapid_* : clés Web Push générées au premier démarrage.
CREATE TABLE notification_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    reminder_days_before INTEGER CHECK (reminder_days_before >= 0),
    vapid_public_key VARCHAR(255),
    vapid_private_key TEXT,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO notification_settings (id, reminder_days_before, updated_at) VALUES (1, NULL, now());

-- Rappels déjà envoyés : un seul rappel par membre et par période de cotisation.
CREATE TABLE contribution_reminder (
    contribution_period_id UUID NOT NULL REFERENCES contribution_period (id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    sent_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (contribution_period_id, member_id)
);
