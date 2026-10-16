-- Décision n° 63 : date de la dernière réinitialisation des données par le super
-- administrateur. Une fois renseignée, les données de démonstration (profil "demo")
-- ne sont plus jamais recréées au démarrage.
ALTER TABLE system_settings ADD COLUMN data_reset_at TIMESTAMPTZ;
