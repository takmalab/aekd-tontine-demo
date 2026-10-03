/** Miroir de AuditAction (backend). */
export type AuditAction =
  | 'SESSION_CREATED'
  | 'SESSION_UPDATED'
  | 'SESSION_DELETED'
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
  | 'LOAN_POLICY_CREATED'
  | 'LOAN_POLICY_UPDATED'
  | 'LOAN_POLICY_ACTIVATED'
  | 'LOAN_POLICY_DEACTIVATED'
  | 'LOAN_POLICY_DELETED'
  | 'USER_CREATED'
  | 'USER_REGISTERED'
  | 'USER_APPROVED'
  | 'USER_UPDATED';

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
  LOAN_POLICY_CREATED: 'Politique de prêt créée',
  LOAN_POLICY_UPDATED: 'Politique de prêt modifiée',
  LOAN_POLICY_ACTIVATED: 'Politique de prêt activée',
  LOAN_POLICY_DEACTIVATED: 'Politique de prêt désactivée',
  LOAN_POLICY_DELETED: 'Politique de prêt supprimée',
  USER_CREATED: 'Compte créé',
  USER_REGISTERED: 'Auto-inscription',
  USER_APPROVED: 'Compte validé',
  USER_UPDATED: 'Compte modifié',
};
