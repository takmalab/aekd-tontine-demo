-- Période du taux d'intérêt (docs/decisions.md n° 53) : le taux de la politique
-- s'applique par période de N mois (1 = mensuel, 2 = bimestriel, 3 = trimestriel…),
-- au prorata de la durée. Figée sur le prêt à l'approbation, comme le taux.
ALTER TABLE loan_policy ADD COLUMN interest_period_months INTEGER NOT NULL DEFAULT 1
    CHECK (interest_period_months >= 1);
ALTER TABLE loan ADD COLUMN interest_period_months INTEGER CHECK (interest_period_months >= 1);
-- Prêts déjà approuvés : leur taux était mensuel.
UPDATE loan SET interest_period_months = 1 WHERE approved_amount IS NOT NULL;

-- Nombre maximum de prêts qu'un même membre peut garantir comme avaliste (n° 53) ;
-- dépassement signalé au trésorier. NULL = pas de limite.
ALTER TABLE loan_policy ADD COLUMN max_guarantees_per_member INTEGER CHECK (max_guarantees_per_member >= 1);
