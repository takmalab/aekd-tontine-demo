-- Photo de profil d'un compte (facultative). Le fichier est sur le stockage
-- (FileStorageService), seule sa clé est en base.
ALTER TABLE users ADD COLUMN photo_storage_key VARCHAR(500);
ALTER TABLE users ADD COLUMN photo_content_type VARCHAR(100);
