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
}
