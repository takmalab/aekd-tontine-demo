export type AmountMode = 'FIXED' | 'VOLUNTARY';
export type ContributionFrequency = 'MONTHLY';
export type Visibility = 'PUBLIC' | 'PRIVATE';
export type FundDestination = 'TONTINE_FUND' | 'INDIVIDUAL_SAVINGS';
export type ContributionStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE';

/** Miroir de ContributionDefinitionResponse (backend). */
export interface ContributionDefinition {
  id: string;
  name: string;
  description: string | null;
  amount: number | null;
  amountMode: AmountMode;
  frequency: ContributionFrequency;
  mandatory: boolean;
  visibility: Visibility;
  fundDestination: FundDestination;
  status: ContributionStatus;
}

/** Miroir de ContributionDefinitionRequest (backend). La cotisation est créée en DRAFT. */
export interface ContributionDefinitionRequest {
  name: string;
  description: string | null;
  /** Requis (> 0) pour FIXED ; null pour VOLUNTARY. */
  amount: number | null;
  amountMode: AmountMode;
  frequency: ContributionFrequency;
  mandatory: boolean;
  visibility: Visibility;
  fundDestination: FundDestination;
}

/** Miroir de ParticipantResponse / BeneficiaryResponse (backend) — CLAUDE.md §10 et §12. */
export interface ContributionMemberLink {
  id: string;
  memberId: string;
  memberFullName: string;
}

/** Miroir de ContributionPeriodRequest (backend). */
export interface ContributionPeriodRequest {
  sessionId: string;
  dueDate: string | null;
}

/** Miroir de ContributionPeriodResponse (backend). */
export interface ContributionPeriod {
  id: string;
  contributionDefinitionId: string;
  sessionId: string;
  sessionLabel: string;
  dueDate: string | null;
}

export const FUND_DESTINATION_LABELS: Record<FundDestination, string> = {
  TONTINE_FUND: 'Fonds commun',
  INDIVIDUAL_SAVINGS: 'Épargne individuelle',
};

export const CONTRIBUTION_STATUS_LABELS: Record<ContributionStatus, string> = {
  DRAFT: 'Brouillon',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

export const FREQUENCY_LABELS: Record<ContributionFrequency, string> = {
  MONTHLY: 'Mensuelle',
};

export const AMOUNT_MODE_LABELS: Record<AmountMode, string> = {
  FIXED: 'Montant fixe',
  VOLUNTARY: 'Montant libre',
};

export const VISIBILITY_LABELS: Record<Visibility, string> = {
  PUBLIC: 'Publique',
  PRIVATE: 'Privée',
};

// ---------- Paiements (CLAUDE.md §13-§16) ----------

/** CLAUDE.md §15 : déclaration manuelle, aucune intégration réelle dans le MVP. */
export type PaymentOperator = 'MTN_MOMO' | 'ORANGE_MONEY' | 'BANK_TRANSFER' | 'CASH';

/** CLAUDE.md §14 : seul VALIDATED compte dans les fonds. */
export type ContributionTransactionStatus = 'PENDING' | 'VALIDATED' | 'REJECTED';

/** CLAUDE.md §13 : situation d'un membre pour une période. */
export type ContributionPeriodStatus = 'PAID' | 'PENDING' | 'LATE' | 'NOT_PAID';

export const PAYMENT_OPERATOR_LABELS: Record<PaymentOperator, string> = {
  MTN_MOMO: 'MTN Mobile Money',
  ORANGE_MONEY: 'Orange Money',
  BANK_TRANSFER: 'Virement bancaire',
  CASH: 'Espèces',
};

export const TRANSACTION_STATUS_LABELS: Record<ContributionTransactionStatus, string> = {
  PENDING: 'En attente de validation',
  VALIDATED: 'Validé',
  REJECTED: 'Rejeté',
};

export const PERIOD_STATUS_LABELS: Record<ContributionPeriodStatus, string> = {
  PAID: 'Payée',
  PENDING: 'En attente',
  LATE: 'En retard',
  NOT_PAID: 'À payer',
};

/** Miroir de ContributionTransactionRequest (backend). */
export interface ContributionTransactionRequest {
  contributionPeriodId: string;
  /** Toujours envoyé ; pour FIXED il doit être égal au montant de la cotisation. */
  amount: number;
  operator: PaymentOperator;
  transactionReference: string;
  paymentDate: string;
  observation: string | null;
}

/** Miroir de ContributionTransactionResponse (backend). */
export interface ContributionTransaction {
  id: string;
  memberId: string;
  memberFullName: string;
  contributionDefinitionId: string;
  contributionDefinitionName: string;
  contributionPeriodId: string;
  sessionId: string;
  sessionLabel: string;
  amount: number;
  operator: PaymentOperator;
  transactionReference: string;
  paymentDate: string;
  observation: string | null;
  status: ContributionTransactionStatus;
  validatedByEmail: string | null;
  validatedAt: string | null;
}

/** Miroir de PaymentProofResponse (backend). */
export interface PaymentProof {
  id: string;
  contributionTransactionId: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  uploadedByEmail: string;
  uploadedAt: string;
}
