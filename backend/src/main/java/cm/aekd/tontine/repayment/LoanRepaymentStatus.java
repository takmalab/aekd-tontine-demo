package cm.aekd.tontine.repayment;

/**
 * Un remboursement déclaré par le membre est PENDING ; seul un remboursement
 * VALIDATED réduit le solde du prêt (docs/decisions.md n° 38). Un remboursement
 * saisi directement par le trésorier est VALIDATED dès sa création.
 */
public enum LoanRepaymentStatus {
    PENDING,
    VALIDATED,
    REJECTED
}
