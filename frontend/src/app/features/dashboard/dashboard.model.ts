export interface SessionSummary {
  id: string;
  label: string;
  /** Jour de la séance (LocalDate "YYYY-MM-DD"). */
  date: string;
  location: string | null;
}

export interface AdminDashboardResponse {
  memberCount: number;
  currentSession: SessionSummary | null;
  mandatoryContributionsExpected: number;
  paymentsValidated: number;
  paymentsPending: number;
  lateContributions: number;
  amountCollected: number;
  /** Fonds commun restant : collecté − remis aux bénéficiaires (décision n° 46). */
  tontineFundBalance: number;
  tontineFundCollected: number;
  tontineFundRemitted: number;
  totalIndividualSavings: number;
  /** Argent d'épargne disponible pour prêter (docs/decisions.md n° 46). */
  availableForLending: number;
  loanRequestsPending: number;
  loansInProgress: number;
  activeSanctions: number;
}

export interface MemberDashboardResponse {
  currentSession: SessionSummary | null;
  mandatoryContributionsCount: number;
  paidContributionsCount: number;
  pendingContributionsCount: number;
  lateContributionsCount: number;
  optionalContributionsCount: number;
  individualSavings: number;
  activeLoansCount: number;
  pendingLoanRequestsCount: number;
  totalRepaid: number;
  activeSanctionsCount: number;
}

export interface StatTile {
  icon: string;
  value: string;
  label: string;
  tone?: 'default' | 'warn' | 'ok';
  /** Page ouverte au clic sur la tuile (U1), avec ses paramètres de filtre éventuels. */
  link: string[];
  query?: Record<string, string>;
}

/** Raccourci d'action du tableau de bord (U1) ; `badge` : nombre d'éléments à traiter. */
export interface DashboardShortcut {
  icon: string;
  label: string;
  link: string[];
  query?: Record<string, string>;
  badge?: number;
  primary?: boolean;
}
