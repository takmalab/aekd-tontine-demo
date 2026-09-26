package cm.aekd.tontine.contribution;

/**
 * Situation d'un membre pour une période donnée (CLAUDE.md §13).
 * PARTIAL : cotisation à montant fixe partiellement payée (paiements
 * partiels autorisés sur décision du porteur du projet).
 */
public enum ContributionPeriodStatus {
    PAID,
    PENDING,
    PARTIAL,
    LATE,
    NOT_PAID
}
