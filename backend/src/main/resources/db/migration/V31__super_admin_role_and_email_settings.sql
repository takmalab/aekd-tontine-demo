-- Super administrateur (docs/decisions.md n° 56) : tous les droits de l'ADMIN, plus
-- les paramètres du système. Il garde toujours aussi le rôle ADMIN.
INSERT INTO role (id, name) VALUES (gen_random_uuid(), 'SUPER_ADMIN');

-- Le compte administrateur actuel devient super administrateur.
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, role r
WHERE lower(u.email) = 'admin@aekd.cm' AND r.name = 'SUPER_ADMIN';

-- Paramètres e-mail (écran « Paramètres e-mail ») : ligne unique. enabled = false
-- force la simulation même si Brevo est configuré.
CREATE TABLE email_settings (
    id          SMALLINT PRIMARY KEY CHECK (id = 1),
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

INSERT INTO email_settings (id, enabled, updated_at) VALUES (1, TRUE, now());
