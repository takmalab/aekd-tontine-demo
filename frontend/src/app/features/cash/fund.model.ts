/** Miroir de FundOverviewResponse (backend) : écran « Fonds », CLAUDE.md §29, decisions.md n° 50. */
export type Account = 'TONTINE_FUND' | 'SAVINGS' | 'SANCTIONS' | 'CASH_FUND' | 'RATION';
export type FundDirection = 'IN' | 'OUT';
export type FundMovementType = 'CONTRIBUTION' | 'PAYOUT' | 'SAVINGS_DEPOSIT' | 'LOAN_DISBURSED' | 'LOAN_REPAYMENT' | 'SANCTION_PAYMENT';
export type FigureCode =
  | 'COLLECTED'
  | 'REMITTED'
  | 'TOTAL_SAVINGS'
  | 'OUTSTANDING_LOAN_CAPITAL'
  | 'LOAN_INTEREST_COLLECTED';

export interface AccountSummary {
  account: Account;
  balance: number;
  figures: { code: FigureCode; amount: number }[];
}

export interface FundMovement {
  date: string | null;
  type: FundMovementType;
  account: Account;
  direction: FundDirection;
  label: string;
  memberFullName: string;
  amount: number;
}

export interface FundOverview {
  accounts: AccountSummary[];
  movements: FundMovement[];
}

/** Présentation de chaque compte : nom, icône, sens du solde affiché. */
export const ACCOUNT_STYLES: Record<Account, { label: string; icon: string; caption: string; tone: string }> = {
  TONTINE_FUND: { label: 'Fonds commun', icon: 'groups', caption: 'Reste à remettre aux bénéficiaires', tone: 'orange' },
  SAVINGS: { label: 'Épargne et prêts', icon: 'savings', caption: 'Disponible pour prêter', tone: 'brown' },
  SANCTIONS: { label: 'Sanctions', icon: 'gavel', caption: 'Solde du compte', tone: 'red' },
  CASH_FUND: { label: 'Fond de caisse', icon: 'point_of_sale', caption: 'Solde du compte', tone: 'blue' },
  RATION: { label: 'Ration', icon: 'restaurant', caption: 'Reste à remettre aux bénéficiaires', tone: 'green' },
};

export const FIGURE_LABELS: Record<FigureCode, string> = {
  COLLECTED: 'Collecté',
  REMITTED: 'Remis aux bénéficiaires',
  TOTAL_SAVINGS: 'Épargne totale',
  OUTSTANDING_LOAN_CAPITAL: 'Capital prêté non remboursé',
  LOAN_INTEREST_COLLECTED: 'Intérêts perçus',
};

export const MOVEMENT_STYLES: Record<FundMovementType, { label: string; icon: string }> = {
  CONTRIBUTION: { label: 'Cotisation', icon: 'payments' },
  PAYOUT: { label: 'Cagnotte remise', icon: 'redeem' },
  SAVINGS_DEPOSIT: { label: "Versement d'épargne", icon: 'savings' },
  LOAN_DISBURSED: { label: 'Prêt accordé', icon: 'account_balance' },
  LOAN_REPAYMENT: { label: 'Remboursement', icon: 'replay' },
  SANCTION_PAYMENT: { label: 'Paiement de sanction', icon: 'gavel' },
};
