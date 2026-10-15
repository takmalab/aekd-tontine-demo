-- Dépenses et virements entre comptes (docs/decisions.md n° 50) : seulement sur les
-- comptes Sanctions, Fond de caisse et Ration ; le fonds commun et l'épargne sont
-- protégés. Une opération est enregistrée par le trésorier et n'est jamais modifiée.
CREATE TABLE account_operation (
    id              UUID PRIMARY KEY,
    type            VARCHAR(20) NOT NULL CHECK (type IN ('EXPENSE', 'TRANSFER')),
    source_account  VARCHAR(20) NOT NULL CHECK (source_account IN ('SANCTIONS', 'CASH_FUND', 'RATION')),
    target_account  VARCHAR(20) CHECK (target_account IN ('SANCTIONS', 'CASH_FUND', 'RATION')),
    amount          NUMERIC(14, 0) NOT NULL CHECK (amount > 0),
    operation_date  DATE NOT NULL,
    label           VARCHAR(200) NOT NULL,
    observation     TEXT,
    recorded_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_account_operation_target CHECK (
        (type = 'EXPENSE' AND target_account IS NULL)
        OR (type = 'TRANSFER' AND target_account IS NOT NULL AND target_account <> source_account))
);

CREATE INDEX idx_account_operation_created ON account_operation (created_at);
