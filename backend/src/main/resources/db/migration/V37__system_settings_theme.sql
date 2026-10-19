-- Décision n° 72 : thème de l'application, réglé par le super administrateur, pour
-- qu'une autre association puisse utiliser l'application à ses couleurs.
-- NULL = valeur par défaut (thème AEKD). Le logo existant (logo_storage_key) sert
-- désormais partout : interface, icônes de l'application installée, PDF.
ALTER TABLE system_settings
    ADD COLUMN theme_primary_color VARCHAR(7),
    ADD COLUMN theme_secondary_color VARCHAR(7),
    ADD COLUMN theme_dark_color VARCHAR(7),
    ADD COLUMN theme_heading_font VARCHAR(40),
    ADD COLUMN theme_body_font VARCHAR(40),
    ADD COLUMN theme_pattern VARCHAR(20),
    ADD COLUMN login_slogan VARCHAR(150),
    ADD COLUMN login_image_storage_key VARCHAR(500),
    ADD COLUMN login_image_content_type VARCHAR(100);
