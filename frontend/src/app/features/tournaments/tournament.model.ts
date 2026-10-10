import { ContributionStatus } from '../contributions/contribution.model';

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** Rang du jour dans le mois : 1 à 4, ou -1 pour le dernier. */
export type WeekOfMonth = 1 | 2 | 3 | 4 | -1;

/**
 * ACTIVE : génère ses séances. STOPPED : arrêté. COMPLETED : tous les membres de
 * l'ordre de passage ont eu leur séance (docs/decisions.md n° 43).
 */
export type TournamentStatus = 'ACTIVE' | 'STOPPED' | 'COMPLETED';

/** Miroir de TournamentResponse.Slot (backend). */
export interface TournamentSlot {
  position: number;
  memberId: string;
  memberFullName: string;
  plannedDate: string;
  /** La séance du passage a été créée (elle a pu être supprimée depuis : sessionId null). */
  generated: boolean;
  sessionId: string | null;
  sessionLabel: string | null;
}

/** Miroir de TournamentResponse (backend). */
export interface Tournament {
  id: string;
  name: string;
  status: TournamentStatus;
  /** "YYYY-MM" */
  startMonth: string;
  weekOfMonth: WeekOfMonth;
  dayOfWeek: DayOfWeek;
  leadDays: number;
  slots: TournamentSlot[];
  contributions: { id: string; name: string; status: ContributionStatus }[];
  createdByEmail: string | null;
  createdAt: string;
}

/** Miroir de TournamentRequest (backend). `memberIds` = ordre de passage. */
export interface TournamentRequest {
  name: string;
  startMonth: string;
  weekOfMonth: WeekOfMonth;
  dayOfWeek: DayOfWeek;
  leadDays: number;
  memberIds: string[];
  contributionDefinitionIds: string[];
}

/** Miroir de TournamentUpdateRequest (backend) : l'ordre et la règle de date ne changent pas. */
export interface TournamentUpdateRequest {
  name: string;
  leadDays: number;
  contributionDefinitionIds: string[];
}

export const DAYS_OF_WEEK: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

export const DAY_OF_WEEK_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'lundi',
  TUESDAY: 'mardi',
  WEDNESDAY: 'mercredi',
  THURSDAY: 'jeudi',
  FRIDAY: 'vendredi',
  SATURDAY: 'samedi',
  SUNDAY: 'dimanche',
};

export const WEEKS_OF_MONTH: WeekOfMonth[] = [1, 2, 3, 4, -1];

export const WEEK_OF_MONTH_LABELS: Record<WeekOfMonth, string> = {
  1: '1er',
  2: '2e',
  3: '3e',
  4: '4e',
  [-1]: 'dernier',
};

export const TOURNAMENT_STATUS_LABELS: Record<TournamentStatus, string> = {
  ACTIVE: 'En cours',
  STOPPED: 'Arrêté',
  COMPLETED: 'Terminé',
};

/** Ex. : « Le 1er samedi du mois », « Le dernier dimanche du mois ». */
export function ruleLabel(weekOfMonth: WeekOfMonth, dayOfWeek: DayOfWeek): string {
  return `Le ${WEEK_OF_MONTH_LABELS[weekOfMonth]} ${DAY_OF_WEEK_LABELS[dayOfWeek]} du mois`;
}
