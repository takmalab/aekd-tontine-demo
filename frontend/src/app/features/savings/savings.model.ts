import { PaymentOperator } from '../contributions/contribution.model';

/** Statut d'un mouvement d'épargne : seul VALIDATED compte (docs/decisions.md n° 39). */
export type SavingsStatus = 'PENDING' | 'VALIDATED' | 'REJECTED';

/** DEPOSIT : versement libre (« Épargner ») ; CONTRIBUTION : cotisation « Banque / Épargne ». */
export type SavingsSource = 'DEPOSIT' | 'CONTRIBUTION';

export const SAVINGS_STATUS_LABELS: Record<SavingsStatus, string> = {
  PENDING: 'En attente de validation',
  VALIDATED: 'Validé',
  REJECTED: 'Rejeté',
};

export const SAVINGS_SOURCE_LABELS: Record<SavingsSource, string> = {
  DEPOSIT: 'Versement libre',
  CONTRIBUTION: 'Cotisation',
};

/** Miroir de SavingsDtos.EntryResponse. `interest` : null si non validé ou aucun taux fixé. */
export interface SavingsEntry {
  id: string;
  source: SavingsSource;
  memberId: string;
  memberFullName: string;
  label: string;
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
  observation: string | null;
  status: SavingsStatus;
  declaredAt: string;
  validatedAt: string | null;
  validatedByEmail: string | null;
  completeMonths: number;
  interest: number | null;
  hasProof: boolean;
}

/** Miroir de SavingsDtos.MemberSavingsResponse. */
export interface MemberSavings {
  memberId: string;
  memberFullName: string;
  principal: number;
  /** Null tant qu'aucun taux n'a été fixé. */
  interest: number | null;
  total: number;
  pendingAmount: number;
  pendingCount: number;
  validatedCount: number;
  /** Taux mensuel en vigueur (en %), ou null. */
  currentMonthlyRate: number | null;
  rateConfigured: boolean;
  entries: SavingsEntry[];
}

export interface SavingsMemberRow {
  memberId: string;
  memberFullName: string;
  active: boolean;
  principal: number;
  interest: number;
  total: number;
  validatedCount: number;
  pendingAmount: number;
  lastDepositDate: string | null;
}

export interface SavingsMonthlyPoint {
  /** « 2026-10 » */
  month: string;
  amount: number;
  count: number;
}

/** Miroir de SavingsDtos.OverviewResponse (ADMIN / TRESORIER). */
export interface SavingsOverview {
  totalPrincipal: number;
  totalInterest: number;
  total: number;
  saversCount: number;
  membersCount: number;
  averagePerSaver: number;
  pendingCount: number;
  pendingAmount: number;
  validatedThisMonth: number;
  currentMonthlyRate: number | null;
  rateConfigured: boolean;
  members: SavingsMemberRow[];
  monthly: SavingsMonthlyPoint[];
}

export interface SavingsDepositRequest {
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
  observation: string | null;
}

/** Taux d'intérêt MENSUEL (en %), applicable à partir d'une date. */
export interface SavingsRate {
  id: string;
  monthlyRate: number;
  effectiveFrom: string;
  note: string | null;
  createdByEmail: string;
  createdAt: string;
}

export interface SavingsRateRequest {
  monthlyRate: number;
  effectiveFrom: string;
  note: string | null;
}

/** « 1,5 % / mois » */
export function formatMonthlyRate(rate: number | null): string {
  return rate === null
    ? 'Non configuré'
    : `${rate.toLocaleString('fr-FR', { maximumFractionDigits: 3 })} % / mois`;
}
