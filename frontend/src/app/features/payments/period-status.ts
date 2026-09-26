import { toIsoDate } from '../../shared/utils/format';
import {
  AmountMode,
  ContributionPeriodStatus,
  ContributionTransaction,
} from '../contributions/contribution.model';

/** Ce dont le calcul a besoin sur la cotisation. */
export interface AmountRule {
  amountMode: AmountMode;
  amount: number | null;
}

function isFixed(rule: AmountRule): rule is AmountRule & { amount: number } {
  return rule.amountMode === 'FIXED' && rule.amount !== null;
}

/** Somme des paiements VALIDÉS d'une période. */
export function validatedAmount(periodId: string, transactions: ContributionTransaction[]): number {
  return transactions
    .filter((t) => t.contributionPeriodId === periodId && t.status === 'VALIDATED')
    .reduce((sum, t) => sum + t.amount, 0);
}

/** Reste dû pour un montant fixe (jamais négatif) ; null pour un montant libre. */
export function remainingAmount(
  periodId: string,
  rule: AmountRule,
  transactions: ContributionTransaction[],
): number | null {
  return isFixed(rule) ? Math.max(0, rule.amount - validatedAmount(periodId, transactions)) : null;
}

/**
 * Miroir exact de ContributionPeriodStatusService.resolve() (backend, CLAUDE.md §13).
 *
 * Montant fixe (paiements partiels autorisés) :
 * - PAID si la somme validée atteint le montant ;
 * - PENDING si un paiement est en attente ;
 * - PARTIAL si 0 < somme validée < montant (prioritaire sur LATE — décision à valider) ;
 * - LATE si rien n'est validé et l'échéance est strictement dépassée ;
 * - NOT_PAID sinon.
 *
 * Montant libre (inchangé) : PAID si un paiement validé existe, PENDING si un
 * paiement est en attente, puis LATE / NOT_PAID.
 */
export function resolvePeriodStatus(
  periodId: string,
  dueDate: string | null,
  transactions: ContributionTransaction[],
  rule: AmountRule,
  today: Date = new Date(),
): ContributionPeriodStatus {
  const mine = transactions.filter((t) => t.contributionPeriodId === periodId);
  const hasPending = mine.some((t) => t.status === 'PENDING');
  const overdue = dueDate !== null && toIsoDate(today) > dueDate;

  if (isFixed(rule)) {
    const validated = validatedAmount(periodId, transactions);
    if (validated >= rule.amount) {
      return 'PAID';
    }
    if (hasPending) {
      return 'PENDING';
    }
    if (validated > 0) {
      return 'PARTIAL';
    }
    return overdue ? 'LATE' : 'NOT_PAID';
  }

  if (mine.some((t) => t.status === 'VALIDATED')) {
    return 'PAID';
  }
  if (hasPending) {
    return 'PENDING';
  }
  return overdue ? 'LATE' : 'NOT_PAID';
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

/**
 * Une nouvelle déclaration est possible s'il n'y a rien en attente et que la
 * période n'est pas entièrement payée (règle backend : 409 sinon).
 */
export function canDeclare(status: ContributionPeriodStatus): boolean {
  return status === 'NOT_PAID' || status === 'LATE' || status === 'PARTIAL';
}
