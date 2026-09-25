-- Séance = un jour de réunion unique (et non plus une période) avec un lieu,
-- un membre récepteur (hôte) et un ou plusieurs bénéficiaires de la séance.
-- Décision du porteur du projet (voir docs / rapport de livraison).
--
-- La colonne s'appelle session_date (et non "date") pour éviter le mot-clé SQL.

-- 1. Date unique : reprise de l'ancienne date de début pour les séances existantes.
ALTER TABLE session ADD COLUMN session_date DATE;
UPDATE session SET session_date = start_date;
ALTER TABLE session ALTER COLUMN session_date SET NOT NULL;

ALTER TABLE session DROP COLUMN start_date;
ALTER TABLE session DROP COLUMN end_date;

CREATE INDEX idx_session_session_date ON session (session_date);

-- 2. Lieu (quartier / ville). Nullable en base : les séances créées avant cette
--    migration n'en ont pas ; l'API l'exige pour toute nouvelle séance.
ALTER TABLE session ADD COLUMN location VARCHAR(150);

-- 3. Membre récepteur de la séance. Nullable pour la même raison ; si le membre
--    est supprimé, la séance est conservée sans récepteur.
ALTER TABLE session ADD COLUMN host_member_id UUID REFERENCES member (id) ON DELETE SET NULL;

-- 4. Bénéficiaire(s) de la séance : concept distinct du bénéficiaire d'une
--    cotisation (contribution_beneficiary, CLAUDE.md §12).
CREATE TABLE session_beneficiary (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_session_beneficiary UNIQUE (session_id, member_id)
);

CREATE INDEX idx_session_beneficiary_session ON session_beneficiary (session_id);
