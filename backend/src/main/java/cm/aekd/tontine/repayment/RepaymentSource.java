package cm.aekd.tontine.repayment;

/** Origine de l'argent d'un remboursement de prêt (decisions.md n° 53). */
public enum RepaymentSource {
    /** Versé par le membre (déclaré ou saisi par le trésorier). */
    DIRECT,
    /** Retenu sur une cagnotte remise (emprunteur ou avaliste). */
    PAYOUT_DEDUCTION,
    /** Prélevé sur l'épargne individuelle (emprunteur ou avaliste). */
    SAVINGS_DEDUCTION
}
