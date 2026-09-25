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
