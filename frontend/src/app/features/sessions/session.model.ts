import { ContributionDefinition, ContributionPeriod } from '../contributions/contribution.model';

/** Membre résumé (id + nom), tel que renvoyé pour le récepteur de la séance. */
export interface SessionMember {
  memberId: string;
  memberFullName: string;
}

/** Bénéficiaire d'une séance (distinct du bénéficiaire d'une cotisation). */
export interface SessionBeneficiary extends SessionMember {
  id: string;
}

/**
 * Miroir de SessionResponse (backend). Une séance est une réunion tenue un jour
 * donné. `location`, `hostMember` et `beneficiaries` peuvent être absents pour
 * les séances créées avant l'ajout de ces informations.
 */
export interface Session {
  id: string;
  label: string;
  date: string;
  location: string | null;
  hostMember: SessionMember | null;
  beneficiaries: SessionBeneficiary[];
}

/** Miroir de SessionRequest (backend). */
export interface SessionRequest {
  label: string;
  date: string;
  location: string;
  hostMemberId: string;
  beneficiaryMemberIds: string[];
}

/** Situation calendaire d'une séance, déduite uniquement de sa date (affichage). */
export type SessionTiming = 'CURRENT' | 'UPCOMING' | 'PAST';

export const SESSION_TIMING_LABELS: Record<SessionTiming, string> = {
  CURRENT: "Aujourd'hui",
  UPCOMING: 'À venir',
  PAST: 'Passée',
};

/** Une cotisation telle qu'appliquée à une séance (définition + période). */
export interface SessionContribution {
  definition: ContributionDefinition;
  period: ContributionPeriod;
}
