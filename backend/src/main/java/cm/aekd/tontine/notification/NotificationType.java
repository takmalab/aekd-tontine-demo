package cm.aekd.tontine.notification;

/** Événements notifiés (choix du propriétaire du projet, docs/decisions.md n° 44). */
public enum NotificationType {
    // Membres
    SESSION_CREATED,
    SESSION_CONTRIBUTION_ADDED,
    PAYMENT_VALIDATED,
    PAYMENT_REJECTED,
    LOAN_REPAYMENT_VALIDATED,
    LOAN_REPAYMENT_REJECTED,
    LOAN_REPAYMENT_RECORDED,
    SAVINGS_DEPOSIT_VALIDATED,
    SAVINGS_DEPOSIT_REJECTED,
    LOAN_APPROVED,
    LOAN_REJECTED,
    SANCTION_APPLIED,
    SANCTION_CANCELLED,
    CONTRIBUTION_REMINDER,
    // Bénéficiaire : remise de sa part de la cagnotte (docs/decisions.md n° 46)
    SESSION_PAYOUT_RECEIVED,
    // Tous les membres : mouvement d'un autre membre (docs/decisions.md n° 45)
    MEMBER_ACTIVITY,
    // Trésorier et administrateur
    PAYMENT_DECLARED,
    LOAN_REPAYMENT_DECLARED,
    SAVINGS_DEPOSIT_DECLARED,
    LOAN_REQUESTED,
    USER_REGISTERED,
    TOURNAMENT_SESSION_GENERATED,
    TOURNAMENT_COMPLETED,
    // Vérification de l'abonnement push depuis l'interface
    TEST
}
