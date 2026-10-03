-- Remboursements de prêt déclarés par le membre (docs/decisions.md n° 38) :
-- en attente jusqu'à validation par le trésorier, avec un justificatif facultatif.
-- Les remboursements existants ont été saisis par le trésorier : ils sont validés.

ALTER TABLE loan_repayment ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'VALIDATED';
ALTER TABLE loan_repayment ALTER COLUMN status DROP DEFAULT;

ALTER TABLE loan_repayment ADD COLUMN validated_by UUID REFERENCES users (id);
ALTER TABLE loan_repayment ADD COLUMN validated_at TIMESTAMPTZ;
UPDATE loan_repayment SET validated_by = recorded_by, validated_at = created_at;

-- Justificatif facultatif (JPG, JPEG, PNG, PDF), stocké via FileStorageService.
ALTER TABLE loan_repayment ADD COLUMN proof_storage_key VARCHAR(500);
ALTER TABLE loan_repayment ADD COLUMN proof_original_filename VARCHAR(255);
ALTER TABLE loan_repayment ADD COLUMN proof_content_type VARCHAR(100);
ALTER TABLE loan_repayment ADD COLUMN proof_file_size BIGINT;

CREATE INDEX idx_loan_repayment_status ON loan_repayment (status);
