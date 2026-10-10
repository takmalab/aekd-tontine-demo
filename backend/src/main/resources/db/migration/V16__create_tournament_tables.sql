-- Tournoi : calendrier de séances mensuelles générées automatiquement selon un
-- ordre de passage des membres (docs/decisions.md n° 43).

CREATE TABLE tournament (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    -- Premier mois du tournoi (stocké au 1er du mois) : le membre en position 0
    -- reçoit la séance de ce mois, celui en position 1 celle du mois suivant, etc.
    start_month DATE NOT NULL,
    -- Rang du jour dans le mois : 1 à 4, ou -1 pour le dernier.
    week_of_month SMALLINT NOT NULL CHECK (week_of_month IN (1, 2, 3, 4, -1)),
    day_of_week VARCHAR(10) NOT NULL,
    -- Nombre de jours avant la date de la séance où celle-ci est créée.
    lead_days INTEGER NOT NULL CHECK (lead_days >= 0),
    status VARCHAR(20) NOT NULL,
    created_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Ordre de passage : un membre par mois, récepteur et bénéficiaire de sa séance.
CREATE TABLE tournament_slot (
    id UUID PRIMARY KEY,
    tournament_id UUID NOT NULL REFERENCES tournament (id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position >= 0),
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    -- Séance générée pour ce passage ; reste NULL si la séance est supprimée ensuite
    -- (generated_at indique alors que le passage a déjà été traité).
    session_id UUID REFERENCES session (id) ON DELETE SET NULL,
    generated_at TIMESTAMPTZ,
    CONSTRAINT uk_tournament_slot_position UNIQUE (tournament_id, position),
    CONSTRAINT uk_tournament_slot_member UNIQUE (tournament_id, member_id)
);

CREATE INDEX idx_tournament_slot_tournament ON tournament_slot (tournament_id);

-- Cotisations rattachées par défaut à chaque séance générée.
CREATE TABLE tournament_contribution (
    tournament_id UUID NOT NULL REFERENCES tournament (id) ON DELETE CASCADE,
    contribution_definition_id UUID NOT NULL REFERENCES contribution_definition (id) ON DELETE CASCADE,
    PRIMARY KEY (tournament_id, contribution_definition_id)
);
