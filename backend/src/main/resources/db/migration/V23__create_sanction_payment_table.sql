-- Paiement des sanctions monétaires (docs/decisions.md n° 50) : le membre déclare,
-- éventuellement en plusieurs fois ; le trésorier valide ou rejette. Seuls les
-- paiements validés alimentent le compte Sanctions.
CREATE TABLE sanction_payment (
    id                      UUID PRIMARY KEY,
    applied_sanction_id     UUID NOT NULL REFERENCES applied_sanction (id) ON DELETE CASCADE,
    member_id               UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    amount                  NUMERIC(14, 0) NOT NULL CHECK (amount > 0),
    payment_date            DATE NOT NULL,
    operator                VARCHAR(20) NOT NULL,
    reference               VARCHAR(150) NOT NULL,
    observation             TEXT,
    status                  VARCHAR(20) NOT NULL,
    declared_by             UUID REFERENCES users (id) ON DELETE SET NULL,
    validated_by            UUID REFERENCES users (id) ON DELETE SET NULL,
    validated_at            TIMESTAMPTZ,
    proof_storage_key       VARCHAR(500),
    proof_original_filename VARCHAR(255),
    proof_content_type      VARCHAR(100),
    proof_file_size         BIGINT,
    created_at              TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sanction_payment_sanction ON sanction_payment (applied_sanction_id);
CREATE INDEX idx_sanction_payment_status ON sanction_payment (status, created_at);
