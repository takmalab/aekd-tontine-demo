import { ContributionDefinition, ContributionPeriod, PaymentOperator } from '../contributions/contribution.model';

/** Membre résumé (id + nom), tel que renvoyé pour le récepteur de la séance. */
export interface SessionMember {
  memberId: string;
  memberFullName: string;
}

/** Bénéficiaire d'une séance (distinct du bénéficiaire d'une cotisation). */
export interface SessionBeneficiary extends SessionMember {
  id: string;
}

/**
 * Miroir de SessionResponse (backend). Une séance est une réunion tenue un jour
 * donné. `location`, `hostMember` et `beneficiaries` peuvent être absents pour
 * les séances créées avant l'ajout de ces informations.
 */
export interface Session {
  id: string;
  label: string;
  date: string;
  location: string | null;
  hostMember: SessionMember | null;
  beneficiaries: SessionBeneficiary[];
}

/** Miroir de SessionContributionRequest (backend) : cotisation existante rattachée à la séance. */
export interface SessionContributionRequest {
  contributionDefinitionId: string;
  dueDate: string | null;
}

/**
 * Miroir de SessionRequest (backend). `contributions` remplace l'ensemble des
 * cotisations rattachées à la séance ; absent, les rattachements sont inchangés.
 */
export interface SessionRequest {
  label: string;
  date: string;
  location: string;
  hostMemberId: string;
  beneficiaryMemberIds: string[];
  contributions?: SessionContributionRequest[];
}

/** Situation calendaire d'une séance, déduite uniquement de sa date (affichage). */
export type SessionTiming = 'CURRENT' | 'UPCOMING' | 'PAST';

export const SESSION_TIMING_LABELS: Record<SessionTiming, string> = {
  CURRENT: "Aujourd'hui",
  UPCOMING: 'À venir',
  PAST: 'Passée',
};

/** Une cotisation telle qu'appliquée à une séance (définition + période). */
export interface SessionContribution {
  definition: ContributionDefinition;
  period: ContributionPeriod;
}

// ---------- Cagnotte de la séance (docs/decisions.md n° 46) ----------

export interface SessionPayout {
  id: string;
  sessionId: string;
  memberId: string;
  memberFullName: string;
  amount: number;
  payoutDate: string;
  operator: PaymentOperator;
  reference: string | null;
  observation: string | null;
  recordedByEmail: string | null;
  createdAt: string;
  /** Retenue pour rembourser des prêts ; le bénéficiaire a reçu amount − deductedAmount (n° 53). */
  deductedAmount: number;
}

/** Prêt sur lequel retenir à la remise de la cagnotte (decisions.md n° 53). */
export interface PayoutDeductionOption {
  loanId: string;
  borrowerFullName: string;
  /** BORROWER : son propre prêt ; GUARANTOR : un prêt qu'il garantit. */
  role: 'BORROWER' | 'GUARANTOR';
  outstanding: number;
  overdue: boolean;
  /** Pourquoi la retenue est impossible (ex. l'emprunteur a encore de l'épargne). */
  blockedReason: string | null;
}

export interface PayoutBeneficiaryShare {
  memberId: string;
  memberFullName: string;
  /** Part égale de la cagnotte collectée, arrondie au franc inférieur. */
  share: number;
  remitted: number;
  due: number;
}

/** Miroir de PayoutOverview (backend). */
export interface PayoutOverview {
  sessionId: string;
  sessionLabel: string;
  contributions: { contributionDefinitionId: string; name: string; collected: number }[];
  collected: number;
  remitted: number;
  remaining: number;
  /** Reliquat de la division en parts égales, non attribué (règle à valider). */
  unallocated: number;
  beneficiaries: PayoutBeneficiaryShare[];
  payouts: SessionPayout[];
}

export interface PayoutRequest {
  memberId: string;
  amount: number;
  payoutDate: string;
  operator: PaymentOperator;
  reference: string | null;
  observation: string | null;
  deductions?: { loanId: string; amount: number }[];
}