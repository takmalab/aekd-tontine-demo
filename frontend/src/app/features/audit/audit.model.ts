/** Miroir de AuditAction (backend). */
export type AuditAction =
  | 'SESSION_CREATED'
  | 'SESSION_UPDATED'
  | 'SESSION_DELETED'
  | 'SESSION_CONTRIBUTION_ADDED'
  | 'SESSION_CONTRIBUTION_REMOVED'
  | 'SESSION_CONTRIBUTION_DUE_DATE_CHANGED'
  | 'SESSION_PAYOUT_RECORDED'
  | 'TOURNAMENT_CREATED'
  | 'TOURNAMENT_UPDATED'
  | 'TOURNAMENT_STOPPED'
  | 'TOURNAMENT_COMPLETED'
  | 'TOURNAMENT_DELETED'
  | 'TOURNAMENT_SESSION_GENERATED'
  | 'CONTRIBUTION_CREATED'
  | 'CONTRIBUTION_ACTIVATED'
  | 'CONTRIBUTION_UPDATED'
  | 'CONTRIBUTION_DEACTIVATED'
  | 'CONTRIBUTION_DELETED'
  | 'PAYMENT_DECLARED'
  | 'PAYMENT_VALIDATED'
  | 'PAYMENT_REJECTED'
  | 'SANCTION_RULE_CREATED'
  | 'SANCTION_APPLIED'
  | 'SANCTION_CANCELLED'
  | 'LOAN_REQUESTED'
  | 'LOAN_APPROVED'
  | 'LOAN_REJECTED'
  | 'LOAN_REPAYMENT_RECORDED'
  | 'LOAN_REPAYMENT_DECLARED'
  | 'LOAN_REPAYMENT_VALIDATED'
  | 'LOAN_REPAYMENT_REJECTED'
  | 'SAVINGS_DEPOSIT_DECLARED'
  | 'SAVINGS_DEPOSIT_VALIDATED'
  | 'SAVINGS_DEPOSIT_REJECTED'
  | 'SAVINGS_RATE_SET'
  | 'LOAN_POLICY_CREATED'
  | 'LOAN_POLICY_UPDATED'
  | 'LOAN_POLICY_ACTIVATED'
  | 'LOAN_POLICY_DEACTIVATED'
  | 'LOAN_POLICY_DELETED'
  | 'USER_CREATED'
  | 'USER_REGISTERED'
  | 'USER_APPROVED'
  | 'USER_UPDATED'
  | 'USER_LOGIN_OTP'
  | 'SMS_SETTINGS_UPDATED'
  | 'SMS_TEST_SENT'
  | 'USER_DELETED';

/** Miroir de AuditLogResponse (backend). */
export interface AuditLog {
  id: string;
  occurredAt: string;
  userEmail: string | null;
  action: AuditAction;
  entityType: string;
  entityId: string;
  description: string;
}

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  SESSION_CREATED: 'Séance créée',
  SESSION_UPDATED: 'Séance modifiée',
  SESSION_DELETED: 'Séance supprimée',
  SESSION_CONTRIBUTION_ADDED: 'Cotisation rattachée à une séance',
  SESSION_CONTRIBUTION_REMOVED: "Cotisation retirée d'une séance",
  SESSION_CONTRIBUTION_DUE_DATE_CHANGED: 'Date limite modifiée',
  SESSION_PAYOUT_RECORDED: 'Cagnotte remise',
  TOURNAMENT_CREATED: 'Tournoi créé',
  TOURNAMENT_UPDATED: 'Tournoi modifié',
  TOURNAMENT_STOPPED: 'Tournoi arrêté',
  TOURNAMENT_COMPLETED: 'Tournoi terminé',
  TOURNAMENT_DELETED: 'Tournoi supprimé',
  TOURNAMENT_SESSION_GENERATED: 'Séance générée automatiquement',
  CONTRIBUTION_CREATED: 'Cotisation créée',
  CONTRIBUTION_ACTIVATED: 'Cotisation activée',
  CONTRIBUTION_UPDATED: 'Cotisation modifiée',
  CONTRIBUTION_DEACTIVATED: 'Cotisation désactivée',
  CONTRIBUTION_DELETED: 'Cotisation supprimée',
  PAYMENT_DECLARED: 'Paiement déclaré',
  PAYMENT_VALIDATED: 'Paiement validé',
  PAYMENT_REJECTED: 'Paiement rejeté',
  SANCTION_RULE_CREATED: 'Règle de sanction créée',
  SANCTION_APPLIED: 'Sanction appliquée',
  SANCTION_CANCELLED: 'Sanction annulée',
  LOAN_REQUESTED: 'Prêt demandé',
  LOAN_APPROVED: 'Prêt approuvé',
  LOAN_REJECTED: 'Prêt rejeté',
  LOAN_REPAYMENT_RECORDED: 'Remboursement enregistré',
  LOAN_REPAYMENT_DECLARED: 'Remboursement déclaré',
  LOAN_REPAYMENT_VALIDATED: 'Remboursement validé',
  LOAN_REPAYMENT_REJECTED: 'Remboursement rejeté',
  SAVINGS_DEPOSIT_DECLARED: "Versement d'épargne déclaré",
  SAVINGS_DEPOSIT_VALIDATED: "Versement d'épargne validé",
  SAVINGS_DEPOSIT_REJECTED: "Versement d'épargne rejeté",
  SAVINGS_RATE_SET: "Taux d'épargne fixé",
  LOAN_POLICY_CREATED: 'Politique de prêt créée',
  LOAN_POLICY_UPDATED: 'Politique de prêt modifiée',
  LOAN_POLICY_ACTIVATED: 'Politique de prêt activée',
  LOAN_POLICY_DEACTIVATED: 'Politique de prêt désactivée',
  LOAN_POLICY_DELETED: 'Politique de prêt supprimée',
  USER_CREATED: 'Compte créé',
  USER_REGISTERED: 'Auto-inscription',
  USER_APPROVED: 'Compte validé',
  USER_UPDATED: 'Compte modifié',
  USER_LOGIN_OTP: 'Connexion par code SMS',
  SMS_SETTINGS_UPDATED: 'Fournisseur SMS modifié',
  SMS_TEST_SENT: 'SMS de test envoyé',
  USER_DELETED: 'Compte supprimé',
};
