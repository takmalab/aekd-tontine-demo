-- Épargne individuelle (docs/decisions.md n° 39).
-- Versements libres déclarés par le membre à tout moment, validés par le trésorier.
-- L'épargne versée via la cotisation « Banque / Épargne » reste dans contribution_transaction.

CREATE TABLE savings_deposit (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    amount NUMERIC(14, 0) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL,
    operator VARCHAR(20) NOT NULL,
    reference VARCHAR(150) NOT NULL,
    observation TEXT,
    status VARCHAR(20) NOT NULL,
    declared_by UUID NOT NULL REFERENCES users (id),
    validated_by UUID REFERENCES users (id),
    validated_at TIMESTAMPTZ,
    proof_storage_key VARCHAR(500),
    proof_original_filename VARCHAR(255),
    proof_content_type VARCHAR(100),
    proof_file_size BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_savings_deposit_member ON savings_deposit (member_id);
CREATE INDEX idx_savings_deposit_status ON savings_deposit (status);

-- Taux d'intérêt MENSUEL de l'épargne, en pourcentage (ex. 1.000 = 1 % par mois).
-- Aucun taux par défaut : tant qu'aucun n'est fixé, aucun intérêt n'est calculé.
-- Historique conservé : chaque mois d'épargne utilise le taux en vigueur à sa date de fin.
CREATE TABLE savings_interest_rate (
    id UUID PRIMARY KEY,
    monthly_rate NUMERIC(6, 3) NOT NULL CHECK (monthly_rate >= 0 AND monthly_rate <= 100),
    effective_from DATE NOT NULL UNIQUE,
    note VARCHAR(255),
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);
