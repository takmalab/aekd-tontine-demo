package cm.aekd.tontine.audit;

/**
 * Actions tracées (CLAUDE.md §26). Limité aux opérations qui existent
 * réellement dans le code à ce jour ; d'autres valeurs pourront être
 * ajoutées sans rien casser au fur et à mesure des futures fonctionnalités.
 */
public enum AuditAction {
    SESSION_CREATED,
    CONTRIBUTION_CREATED,
    CONTRIBUTION_ACTIVATED,
    PAYMENT_DECLARED,
    PAYMENT_VALIDATED,
    PAYMENT_REJECTED,
    SANCTION_RULE_CREATED,
    SANCTION_APPLIED,
    SANCTION_CANCELLED,
    LOAN_REQUESTED,
    LOAN_APPROVED,
    LOAN_REJECTED,
    LOAN_REPAYMENT_RECORDED
}
