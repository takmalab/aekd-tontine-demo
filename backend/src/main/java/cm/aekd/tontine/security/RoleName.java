package cm.aekd.tontine.security;

/**
 * Les trois rôles applicatifs définis dans CLAUDE.md (§6).
 * Référence unique : réutilisé par la future entité Role/User (étape 8)
 * et par la configuration Spring Security.
 */
public enum RoleName {
    ADMIN,
    TRESORIER,
    MEMBRE
}
