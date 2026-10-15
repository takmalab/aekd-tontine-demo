-- Avaliste (garant) d'un prêt (docs/decisions.md n° 52) : désigné par le membre à
-- la demande, il accepte ou refuse ; le trésorier n'approuve qu'après son accord.
-- La politique de prêt indique si un avaliste est obligatoire.
ALTER TABLE loan_policy ADD COLUMN guarantor_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE loan ADD COLUMN guarantor_member_id UUID REFERENCES member (id) ON DELETE SET NULL;
ALTER TABLE loan ADD COLUMN guarantor_status VARCHAR(20)
    CHECK (guarantor_status IN ('PENDING', 'ACCEPTED', 'DECLINED'));
ALTER TABLE loan ADD COLUMN guarantor_decided_at TIMESTAMPTZ;

CREATE INDEX idx_loan_guarantor ON loan (guarantor_member_id);
