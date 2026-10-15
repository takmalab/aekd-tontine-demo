-- Comptes Ration et Fond de caisse (docs/decisions.md n° 50).
-- La cotisation « Ration » passe du fonds commun au compte Ration, paiements déjà
-- validés compris : leur argent est désormais compté dans ce compte. Elle reste
-- remise au bénéficiaire de la séance avec le fonds commun.
UPDATE contribution_definition
SET fund_destination = 'RATION'
WHERE lower(trim(name)) = 'ration' AND fund_destination = 'TONTINE_FUND';
