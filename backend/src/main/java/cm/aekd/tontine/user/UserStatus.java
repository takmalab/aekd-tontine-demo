package cm.aekd.tontine.user;

/**
 * Cycle de vie d'un compte. Un compte auto-inscrit démarre en
 * PENDING_VALIDATION et ne peut pas se connecter tant qu'un
 * administrateur ne l'a pas fait passer à ACTIVE.
 */
public enum UserStatus {
    PENDING_VALIDATION,
    ACTIVE,
    DISABLED
}
