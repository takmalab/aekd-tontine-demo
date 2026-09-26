/** Miroir de AuditAction (backend). */
export type AuditAction =
  | 'SESSION_CREATED'
  | 'CONTRIBUTION_CREATED'
  | 'CONTRIBUTION_ACTIVATED'
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
  CONTRIBUTION_CREATED: 'Cotisation créée',
  CONTRIBUTION_ACTIVATED: 'Cotisation activée',
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
  USER_CREATED: 'Compte créé',
  USER_REGISTERED: 'Auto-inscription',
  USER_APPROVED: 'Compte validé',
  USER_UPDATED: 'Compte modifié',
};
