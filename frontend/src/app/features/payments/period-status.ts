import { toIsoDate } from '../../shared/utils/format';
import {
  ContributionPeriodStatus,
  ContributionTransaction,
} from '../contributions/contribution.model';

/**
 * Miroir exact de ContributionPeriodStatusService.resolve() (backend, CLAUDE.md §13) :
 * - PAID si un paiement VALIDATED existe pour ce membre et cette période ;
 * - PENDING si un paiement PENDING existe ;
 * - LATE si l'échéance est définie et dépassée (strictement antérieure à aujourd'hui) ;
 * - NOT_PAID sinon.
 * `transactions` doit contenir les paiements du membre (GET /payments/mine).
 */
export function resolvePeriodStatus(
  periodId: string,
  dueDate: string | null,
  transactions: ContributionTransaction[],
  today: Date = new Date(),
): ContributionPeriodStatus {
  const mine = transactions.filter((t) => t.contributionPeriodId === periodId);
  if (mine.some((t) => t.status === 'VALIDATED')) {
    return 'PAID';
  }
  if (mine.some((t) => t.status === 'PENDING')) {
    return 'PENDING';
  }
  if (dueDate !== null && toIsoDate(today) > dueDate) {
    return 'LATE';
  }
  return 'NOT_PAID';
}

/**
 * Statut affiché : une cotisation facultative n'est jamais présentée « en retard »
 * (CLAUDE.md §18-§19 : le retard ne concerne que les cotisations obligatoires ;
 * le tableau de bord backend ne compte d'ailleurs les retards que sur celles-ci).
 */
export function displayedPeriodStatus(
  status: ContributionPeriodStatus,
  mandatory: boolean,
): ContributionPeriodStatus {
  return !mandatory && status === 'LATE' ? 'NOT_PAID' : status;
}

/** Une nouvelle déclaration n'est possible que sans paiement en attente ou validé (règle backend, 409 sinon). */
export function canDeclare(status: ContributionPeriodStatus): boolean {
  return status === 'NOT_PAID' || status === 'LATE';
}
