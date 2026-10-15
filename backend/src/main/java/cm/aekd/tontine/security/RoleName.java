package cm.aekd.tontine.security;

/**
 * Les rôles applicatifs définis dans CLAUDE.md (§6), plus le super
 * administrateur (docs/decisions.md n° 56) : tous les droits de l'ADMIN et les
 * paramètres du système. Un super administrateur garde toujours aussi le rôle ADMIN.
 * Référence unique : réutilisé par la future entité Role/User (étape 8)
 * et par la configuration Spring Security.
 */
public enum RoleName {
    SUPER_ADMIN,
    ADMIN,
    TRESORIER,
    MEMBRE
}
