export interface SessionSummary {
  id: string;
  label: string;
  startDate: string;
  endDate: string;
}

export interface AdminDashboardResponse {
  memberCount: number;
  currentSession: SessionSummary | null;
  mandatoryContributionsExpected: number;
  paymentsValidated: number;
  paymentsPending: number;
  lateContributions: number;
  amountCollected: number;
  tontineFundBalance: number;
  totalIndividualSavings: number;
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
}
