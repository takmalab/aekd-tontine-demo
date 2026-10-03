-- Suppression d'un compte utilisateur par l'ADMIN (docs/decisions.md n° 41).
--
-- Les données du MEMBRE supprimé (paiements, prêts, remboursements, sanctions,
-- épargne…) partent déjà par ON DELETE CASCADE depuis member -> users.
--
-- En revanche, un compte apparaît aussi comme AUTEUR d'actions sur les données
-- des autres membres (validation, enregistrement, application, dépôt de
-- justificatif, taux…). Ces données ne doivent pas disparaître : on efface
-- seulement la référence à l'auteur (« utilisateur supprimé »).

ALTER TABLE applied_sanction ALTER COLUMN applied_by DROP NOT NULL;
ALTER TABLE applied_sanction DROP CONSTRAINT applied_sanction_applied_by_fkey,
    ADD CONSTRAINT applied_sanction_applied_by_fkey FOREIGN KEY (applied_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE audit_log DROP CONSTRAINT audit_log_user_id_fkey,
    ADD CONSTRAINT audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE contribution_transaction DROP CONSTRAINT contribution_transaction_validated_by_fkey,
    ADD CONSTRAINT contribution_transaction_validated_by_fkey FOREIGN KEY (validated_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE loan DROP CONSTRAINT loan_decision_by_fkey,
    ADD CONSTRAINT loan_decision_by_fkey FOREIGN KEY (decision_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE loan_repayment ALTER COLUMN recorded_by DROP NOT NULL;
ALTER TABLE loan_repayment DROP CONSTRAINT loan_repayment_recorded_by_fkey,
    ADD CONSTRAINT loan_repayment_recorded_by_fkey FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE loan_repayment DROP CONSTRAINT loan_repayment_validated_by_fkey,
    ADD CONSTRAINT loan_repayment_validated_by_fkey FOREIGN KEY (validated_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE payment_proof ALTER COLUMN uploaded_by DROP NOT NULL;
ALTER TABLE payment_proof DROP CONSTRAINT payment_proof_uploaded_by_fkey,
    ADD CONSTRAINT payment_proof_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE savings_deposit ALTER COLUMN declared_by DROP NOT NULL;
ALTER TABLE savings_deposit DROP CONSTRAINT savings_deposit_declared_by_fkey,
    ADD CONSTRAINT savings_deposit_declared_by_fkey FOREIGN KEY (declared_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE savings_deposit DROP CONSTRAINT savings_deposit_validated_by_fkey,
    ADD CONSTRAINT savings_deposit_validated_by_fkey FOREIGN KEY (validated_by) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE savings_interest_rate ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE savings_interest_rate DROP CONSTRAINT savings_interest_rate_created_by_fkey,
    ADD CONSTRAINT savings_interest_rate_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;
