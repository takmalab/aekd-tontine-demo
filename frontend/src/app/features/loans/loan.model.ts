import { PaymentOperator } from '../contributions/contribution.model';

/** CLAUDE.md §22. Les transitions APPROVED -> IN_PROGRESS -> REPAID sont faites par le backend. */
export type LoanStatus = 'REQUESTED' | 'APPROVED' | 'IN_PROGRESS' | 'REJECTED' | 'REPAID';

export const LOAN_STATUS_LABELS: Record<LoanStatus, string> = {
  REQUESTED: 'Demandé',
  APPROVED: 'Approuvé',
  IN_PROGRESS: 'En remboursement',
  REJECTED: 'Rejeté',
  REPAID: 'Remboursé',
};

/** Miroir de LoanPolicyResponse (backend, CLAUDE.md §20). Tous les critères sont facultatifs. */
export interface LoanPolicy {
  id: string;
  name: string;
  description: string | null;
  minAmount: number | null;
  maxAmount: number | null;
  maxDurationMonths: number | null;
  /** Taux d'intérêt en %, par période de `interestPeriodMonths` mois (decisions.md n° 14 et 53). */
  interestRate: number | null;
  /** Période du taux en mois : 1 mensuel, 2 bimestriel, 3 trimestriel… */
  interestPeriodMonths: number;
  minSavingsRequired: number | null;
  minSeniorityMonths: number | null;
  maxActiveLoans: number | null;
  active: boolean;
  /** Un avaliste doit être désigné pour chaque demande (decisions.md n° 52). */
  guarantorRequired: boolean;
  /** Prêts qu'un même membre peut garantir à la fois ; null = sans limite (n° 53). */
  maxGuaranteesPerMember: number | null;
}

/** Périodes proposées pour le taux d'intérêt (decisions.md n° 53). */
export const INTEREST_PERIODS: { months: number; label: string }[] = [
  { months: 1, label: 'Mensuel (par mois)' },
  { months: 2, label: 'Bimestriel (tous les 2 mois)' },
  { months: 3, label: 'Trimestriel (tous les 3 mois)' },
  { months: 6, label: 'Semestriel (tous les 6 mois)' },
  { months: 12, label: 'Annuel (par an)' },
];

/** « par mois », « tous les 2 mois », « par trimestre »… (même libellé que le serveur). */
export function interestPeriodLabel(months: number | null | undefined): string {
  switch (months ?? 1) {
    case 1:
      return 'par mois';
    case 3:
      return 'par trimestre';
    case 6:
      return 'par semestre';
    case 12:
      return 'par an';
    default:
      return `tous les ${months} mois`;
  }
}

/** Miroir de LoanPolicyRequest (backend) — ADMIN uniquement. */
export type LoanPolicyRequest = Omit<LoanPolicy, 'id' | 'active'>;

/** Miroir de LoanRuleEvaluationResponse (backend) : une règle de la politique, respectée ou non. */
export interface LoanRuleEvaluation {
  ruleName: string;
  respected: boolean;
  message: string;
}

/** Libellés des règles évaluées par LoanService.evaluateRules (backend). */
export const LOAN_RULE_LABELS: Record<string, string> = {
  MONTANT_MINIMUM: 'Montant minimum',
  MONTANT_MAXIMUM: 'Montant maximum',
  DUREE_MAXIMALE: 'Durée maximale',
  EPARGNE_MINIMALE: 'Épargne minimale',
  ANCIENNETE_MINIMALE: 'Ancienneté minimale',
  NOMBRE_PRETS_ACTIFS: 'Prêts actifs',
  AVALISTE_A_JOUR: 'Avaliste à jour',
};

/** Réponse de l'avaliste (decisions.md n° 52). */
export type GuarantorStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED';

export const GUARANTOR_STATUS_LABELS: Record<GuarantorStatus, string> = {
  PENDING: 'En attente de son accord',
  ACCEPTED: 'A accepté',
  DECLINED: 'A refusé',
};

/** Membre pouvant être désigné avaliste. */
export interface GuarantorCandidate {
  memberId: string;
  fullName: string;
}

/** Miroir de LoanResponse (backend). */
export interface Loan {
  id: string;
  memberId: string;
  memberFullName: string;
  loanPolicyId: string;
  loanPolicyName: string;
  requestedAmount: number;
  approvedAmount: number | null;
  requestedDurationMonths: number;
  approvedDurationMonths: number | null;
  /** Taux (%) par période : figé une fois le prêt approuvé, sinon celui de la politique. */
  interestRate: number | null;
  /** Période du taux en mois (decisions.md n° 53). */
  interestPeriodMonths: number;
  interestAmount: number;
  /** Capital + intérêts à rembourser. */
  totalDue: number;
  /** Vrai avant l'approbation : intérêts calculés sur la demande, à titre indicatif. */
  interestEstimated: boolean;
  reason: string;
  status: LoanStatus;
  decisionDate: string | null;
  decisionByEmail: string | null;
  rejectionReason: string | null;
  ruleEvaluations: LoanRuleEvaluation[];
  requestedAt: string;
  /** Avaliste (decisions.md n° 52), absent si aucun n'a été désigné. */
  guarantorMemberId: string | null;
  guarantorFullName: string | null;
  guarantorStatus: GuarantorStatus | null;
  guarantorDecidedAt: string | null;
}

/** Miroir de LoanRequest (backend). */
export interface LoanRequest {
  amount: number;
  durationMonths: number;
  reason: string;
  guarantorMemberId?: string | null;
}

/** Miroir de LoanApprovalRequest (backend) — TRESORIER et ADMIN (décision n° 40). */
export interface LoanApprovalRequest {
  approvedAmount: number;
  approvedDurationMonths: number;
}

/** Miroir de LoanRepaymentRequest (backend) — TRESORIER et ADMIN (décision n° 40). */
export interface LoanRepaymentRequest {
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
}

/**
 * Remboursement déclaré par le membre : PENDING jusqu'à la décision du trésorier ;
 * seul un remboursement VALIDATED réduit le solde (docs/decisions.md n° 38).
 */
export type LoanRepaymentStatus = 'PENDING' | 'VALIDATED' | 'REJECTED';

export const REPAYMENT_STATUS_LABELS: Record<LoanRepaymentStatus, string> = {
  PENDING: 'En attente de validation',
  VALIDATED: 'Validé',
  REJECTED: 'Rejeté',
};

/** Miroir de LoanRepaymentResponse (backend). */
export interface LoanRepayment {
  id: string;
  loanId: string;
  memberId: string;
  memberFullName: string;
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
  recordedByEmail: string;
  createdAt: string;
  status: LoanRepaymentStatus;
  validatedByEmail: string | null;
  validatedAt: string | null;
  hasProof: boolean;
}

/**
 * Miroir de LoanBalanceResponse (backend) : capital + intérêts (`totalAmount`) - total remboursé
 * (validé) = solde restant (§23, decisions.md n° 19). `pendingAmount` : remboursements déclarés,
 * en attente de validation.
 */
export interface LoanBalance {
  totalAmount: number;
  totalRepaid: number;
  remainingBalance: number;
  pendingAmount: number;
  principalAmount: number;
  interestAmount: number;
  interestRate: number | null;
}

/** Statuts pour lesquels un solde et un historique de remboursements existent. */
export const LOAN_STATUSES_WITH_BALANCE: LoanStatus[] = ['APPROVED', 'IN_PROGRESS', 'REPAID'];
