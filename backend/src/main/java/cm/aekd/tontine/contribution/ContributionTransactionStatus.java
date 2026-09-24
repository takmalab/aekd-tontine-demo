package cm.aekd.tontine.contribution;

/**
 * CLAUDE.md §14/§16. Un paiement déclaré n'est pas automatiquement
 * encaissé : seul VALIDATED doit être pris en compte dans les fonds.
 */
public enum ContributionTransactionStatus {
    PENDING,
    VALIDATED,
    REJECTED
}
