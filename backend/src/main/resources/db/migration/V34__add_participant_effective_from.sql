-- Décision n° 60 : un membre ajouté à une cotisation déjà publiée ne compte
-- qu'à partir des séances datées du jour de son ajout ou après.
-- NULL : participant d'origine, compté pour toutes les séances.
ALTER TABLE contribution_participant ADD COLUMN effective_from DATE;
