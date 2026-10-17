-- Décisions n° 65 et 66 (test de bout en bout du 2026-10-17, points M2 et M5).
-- recorded_by : trésorier ou administrateur qui a enregistré le paiement pour le membre
--               (espèces remises à la séance) ; NULL quand le membre l'a déclaré lui-même.
-- rejection_reason : motif donné au membre lors du rejet de son paiement.
ALTER TABLE contribution_transaction
    ADD COLUMN recorded_by UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rejection_reason VARCHAR(500);
