package cm.aekd.tontine.sanction;

/** Paiement d'une sanction : seul VALIDATED alimente le compte Sanctions (decisions.md n° 50). */
public enum SanctionPaymentStatus {
    PENDING,
    VALIDATED,
    REJECTED
}
