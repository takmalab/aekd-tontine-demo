import { PaymentOperator } from '../contributions/contribution.model';

/** CLAUDE.md §18 : une sanction monétaire a un montant, une sanction en nature une description. */
export type SanctionType = 'MONETARY' | 'IN_KIND';

export type AppliedSanctionStatus = 'APPLIED' | 'CANCELLED';

export const SANCTION_TYPE_LABELS: Record<SanctionType, string> = {
  MONETARY: 'Monétaire',
  IN_KIND: 'En nature',
};

export const APPLIED_STATUS_LABELS: Record<AppliedSanctionStatus, string> = {
  APPLIED: 'Appliquée',
  CANCELLED: 'Annulée',
};

/** Miroir de SanctionRuleResponse (backend) : la règle configurée, rattachée à une cotisation. */
export interface SanctionRule {
  id: string;
  contributionDefinitionId: string;
  contributionDefinitionName: string;
  type: SanctionType;
  /** Jours de retard après l'échéance à partir desquels un membre devient candidat. */
  lateDaysThreshold: number;
  monetaryAmount: number | null;
  description: string | null;
  active: boolean;
}

/** Miroir de SanctionRuleRequest (backend) — ADMIN et TRESORIER. */
export interface SanctionRuleRequest {
  contributionDefinitionId: string;
  type: SanctionType;
  lateDaysThreshold: number;
  monetaryAmount: number | null;
  description: string | null;
}

/** Miroir de SanctionCandidateResponse (backend) : information seule, rien n'est appliqué automatiquement. */
export interface SanctionCandidate {
  memberId: string;
  memberFullName: string;
  contributionPeriodId: string;
  sessionId: string;
  sessionLabel: string;
}

/** Miroir de AppliedSanctionResponse (backend) : la sanction réellement appliquée à un membre. */
export interface AppliedSanction {
  id: string;
  sanctionRuleId: string;
  type: SanctionType;
  memberId: string;
  memberFullName: string;
  contributionDefinitionId: string;
  contributionDefinitionName: string;
  contributionPeriodId: string;
  sessionId: string;
  sessionLabel: string;
  amount: number | null;
  description: string | null;
  status: AppliedSanctionStatus;
  appliedByEmail: string;
  appliedAt: string;
  /** Paiements validés (decisions.md n° 50) ; 0 pour une sanction en nature. */
  paidAmount: number;
  /** Paiements déclarés, en attente de validation. */
  pendingAmount: number;
}

export type SanctionPaymentStatus = 'PENDING' | 'VALIDATED' | 'REJECTED';

export const SANCTION_PAYMENT_STATUS_LABELS: Record<SanctionPaymentStatus, string> = {
  PENDING: 'En attente',
  VALIDATED: 'Validé',
  REJECTED: 'Rejeté',
};

/** Miroir de SanctionPaymentDtos.PaymentResponse (backend). */
export interface SanctionPayment {
  id: string;
  appliedSanctionId: string;
  memberId: string;
  memberFullName: string;
  sanctionLabel: string;
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
  observation: string | null;
  status: SanctionPaymentStatus;
  hasProof: boolean;
  validatedByEmail: string | null;
  validatedAt: string | null;
  declaredAt: string;
}

export interface SanctionPaymentRequest {
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
  observation: string | null;
}

/** Reste à payer d'une sanction monétaire (hors paiements en attente). */
export function sanctionRemaining(s: AppliedSanction): number {
  return Math.max(0, (s.amount ?? 0) - s.paidAmount - s.pendingAmount);
}
