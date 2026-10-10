-- Remise de la cagnotte au bénéficiaire de la séance (docs/decisions.md n° 46).
-- L'argent des cotisations obligatoires du fonds commun rattachées à la séance
-- n'est pas conservé : il est remis au(x) bénéficiaire(s), à parts égales.
--
-- session_id sans ON DELETE CASCADE : une séance qui a déjà donné lieu à une
-- remise ne peut pas être supprimée (aucune donnée financière ne disparaît, n° 35).
-- member_id en ON DELETE CASCADE : la suppression d'un compte efface son
-- historique (n° 41). recorded_by en SET NULL : « utilisateur supprimé » (V15).

CREATE TABLE session_payout (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES session (id),
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    amount NUMERIC(14, 0) NOT NULL CHECK (amount > 0),
    payout_date DATE NOT NULL,
    operator VARCHAR(20) NOT NULL,
    reference VARCHAR(150),
    observation TEXT,
    recorded_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_session_payout_session ON session_payout (session_id);
CREATE INDEX idx_session_payout_member ON session_payout (member_id);
