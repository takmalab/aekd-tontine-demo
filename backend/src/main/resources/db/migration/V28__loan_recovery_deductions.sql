-- Recouvrement d'un prêt (docs/decisions.md n° 53) : une retenue sur la cagnotte
-- remise ou un prélèvement sur l'épargne est enregistré comme un remboursement
-- validé, avec sa source et le membre dont l'argent est pris (emprunteur ou avaliste).
ALTER TABLE loan_repayment ALTER COLUMN operator DROP NOT NULL;
ALTER TABLE loan_repayment ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'DIRECT'
    CHECK (source IN ('DIRECT', 'PAYOUT_DEDUCTION', 'SAVINGS_DEDUCTION'));
ALTER TABLE loan_repayment ADD COLUMN payer_member_id UUID REFERENCES member (id) ON DELETE SET NULL;
ALTER TABLE loan_repayment ADD COLUMN session_payout_id UUID REFERENCES session_payout (id) ON DELETE SET NULL;
CREATE INDEX idx_loan_repayment_payer ON loan_repayment (payer_member_id, source);

-- Part de la cagnotte retenue pour rembourser des prêts : le bénéficiaire reçoit
-- le montant de la remise moins cette retenue.
ALTER TABLE session_payout ADD COLUMN deducted_amount NUMERIC(14, 0) NOT NULL DEFAULT 0;
