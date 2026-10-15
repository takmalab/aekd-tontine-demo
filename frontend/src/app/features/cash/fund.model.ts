/** Miroir de FundOverviewResponse (backend) : écran « Fonds », CLAUDE.md §29. */
export type Fund = 'TONTINE_FUND' | 'SAVINGS';
export type FundDirection = 'IN' | 'OUT';
export type FundMovementType = 'CONTRIBUTION' | 'PAYOUT' | 'SAVINGS_DEPOSIT' | 'LOAN_DISBURSED' | 'LOAN_REPAYMENT';

export interface FundMovement {
  date: string | null;
  type: FundMovementType;
  fund: Fund;
  direction: FundDirection;
  label: string;
  memberFullName: string;
  amount: number;
}

export interface FundOverview {
  tontineFundCollected: number;
  tontineFundRemitted: number;
  tontineFundBalance: number;
  totalIndividualSavings: number;
  outstandingLoanCapital: number;
  availableForLending: number;
  /** Part d'intérêts des remboursements validés (decisions.md n° 19). */
  loanInterestCollected: number;
  movements: FundMovement[];
}

export const FUND_LABELS: Record<Fund, string> = {
  TONTINE_FUND: 'Fonds commun',
  SAVINGS: 'Épargne',
};

export const MOVEMENT_STYLES: Record<FundMovementType, { label: string; icon: string }> = {
  CONTRIBUTION: { label: 'Cotisation', icon: 'payments' },
  PAYOUT: { label: 'Cagnotte remise', icon: 'redeem' },
  SAVINGS_DEPOSIT: { label: "Versement d'épargne", icon: 'savings' },
  LOAN_DISBURSED: { label: 'Prêt accordé', icon: 'account_balance' },
  LOAN_REPAYMENT: { label: 'Remboursement', icon: 'replay' },
};
