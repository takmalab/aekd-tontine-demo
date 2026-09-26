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
  interestRate: number | null;
  minSavingsRequired: number | null;
  minSeniorityMonths: number | null;
  maxActiveLoans: number | null;
  active: boolean;
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
};

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
  reason: string;
  status: LoanStatus;
  decisionDate: string | null;
  decisionByEmail: string | null;
  rejectionReason: string | null;
  ruleEvaluations: LoanRuleEvaluation[];
  requestedAt: string;
}

/** Miroir de LoanRequest (backend). */
export interface LoanRequest {
  amount: number;
  durationMonths: number;
  reason: string;
}

/** Miroir de LoanApprovalRequest (backend) — TRESORIER uniquement. */
export interface LoanApprovalRequest {
  approvedAmount: number;
  approvedDurationMonths: number;
}

/** Miroir de LoanRepaymentRequest (backend) — TRESORIER uniquement. */
export interface LoanRepaymentRequest {
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
}

/** Miroir de LoanRepaymentResponse (backend). */
export interface LoanRepayment {
  id: string;
  loanId: string;
  amount: number;
  paymentDate: string;
  operator: PaymentOperator;
  reference: string;
  recordedByEmail: string;
  createdAt: string;
}

/** Miroir de LoanBalanceResponse (backend) : montant approuvé - total remboursé = solde restant (§23). */
export interface LoanBalance {
  totalAmount: number;
  totalRepaid: number;
  remainingBalance: number;
}

/** Statuts pour lesquels un solde et un historique de remboursements existent. */
export const LOAN_STATUSES_WITH_BALANCE: LoanStatus[] = ['APPROVED', 'IN_PROGRESS', 'REPAID'];
