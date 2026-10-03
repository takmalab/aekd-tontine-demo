package cm.aekd.tontine.savings;

/** Un versement déclaré par le membre est PENDING ; seul un versement VALIDATED compte dans l'épargne. */
public enum SavingsDepositStatus {
    PENDING,
    VALIDATED,
    REJECTED
}
