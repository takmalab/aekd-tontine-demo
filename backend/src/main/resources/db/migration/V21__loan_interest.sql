-- Intérêts des prêts (docs/decisions.md n° 14 et 19, validés le 2026-10-14) :
-- taux mensuel simple sur le capital approuvé, figé sur le prêt à l'approbation.
-- Les prêts existants gardent 0 d'intérêt : seuls les nouveaux prêts approuvés
-- sont concernés.
ALTER TABLE loan ADD COLUMN interest_rate NUMERIC(6, 2);
ALTER TABLE loan ADD COLUMN interest_amount NUMERIC(14, 0) NOT NULL DEFAULT 0;

COMMENT ON COLUMN loan_policy.interest_rate IS 'Taux d''intérêt mensuel en %, appliqué au capital pour chaque mois de la durée';
COMMENT ON COLUMN loan.interest_rate IS 'Taux mensuel (%) de la politique au moment de l''approbation';
COMMENT ON COLUMN loan.interest_amount IS 'Intérêts dus, arrondis aux 5 FCFA supérieurs';
