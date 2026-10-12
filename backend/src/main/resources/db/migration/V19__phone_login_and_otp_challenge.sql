-- Connexion par OTP (SMS) en plus de email + mot de passe (docs/decisions.md n° 47).

-- Numéro de connexion porté par le compte (ADMIN, TRESORIER et MEMBRE),
-- normalisé au format E.164 camerounais : +237 suivi de 9 chiffres.
ALTER TABLE users ADD COLUMN phone VARCHAR(13);
ALTER TABLE users ADD CONSTRAINT uk_users_phone UNIQUE (phone);
ALTER TABLE users ADD CONSTRAINT ck_users_phone_format CHECK (phone ~ '^\+237[0-9]{9}$');

-- Reprise des numéros déjà saisis sur les fiches membres : seuls ceux qui
-- sont valides une fois normalisés, et portés par un seul membre, sont
-- recopiés. Les autres restent sur la fiche membre, à corriger à la main.
WITH normalized AS (
    SELECT m.user_id,
           regexp_replace(regexp_replace(m.phone, '[\s.()-]', '', 'g'), '^(\+237|00237|237)?', '') AS local_number
    FROM member m
    WHERE m.phone IS NOT NULL
),
valid AS (
    SELECT user_id, '+237' || local_number AS phone
    FROM normalized
    WHERE local_number ~ '^[26][0-9]{8}$'
),
unique_phones AS (
    SELECT phone FROM valid GROUP BY phone HAVING count(*) = 1
)
UPDATE users u
SET phone = v.phone
FROM valid v
JOIN unique_phones up ON up.phone = v.phone
WHERE u.id = v.user_id;

-- Codes OTP envoyés par SMS. Seul le hash du code est conservé.
-- user_id est vide quand le numéro ne correspond à aucun compte actif :
-- la demande est tracée (limites d'envoi identiques) mais aucun SMS n'est
-- envoyé, pour ne pas révéler quels numéros sont inscrits.
CREATE TABLE otp_challenge (
    id          UUID PRIMARY KEY,
    phone       VARCHAR(13) NOT NULL,
    user_id     UUID REFERENCES users (id) ON DELETE CASCADE,
    code_hash   VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_otp_challenge_phone_created ON otp_challenge (phone, created_at DESC);
