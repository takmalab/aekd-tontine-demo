import { ContributionTransaction, ContributionTransactionStatus } from '../contributions/contribution.model';
import { canDeclare, displayedPeriodStatus, resolvePeriodStatus } from './period-status';

const tx = (periodId: string, status: ContributionTransactionStatus): ContributionTransaction => ({
  id: `${periodId}-${status}`,
  memberId: 'm1',
  memberFullName: 'Membre',
  contributionDefinitionId: 'c1',
  contributionDefinitionName: 'Cotisation',
  contributionPeriodId: periodId,
  sessionId: 's1',
  sessionLabel: 'Séance',
  amount: 50000,
  operator: 'MTN_MOMO',
  transactionReference: 'REF',
  paymentDate: '2026-09-10',
  observation: null,
  status,
  validatedByEmail: null,
  validatedAt: null,
});

describe('resolvePeriodStatus (miroir du backend)', () => {
  const today = new Date(2026, 8, 20); // 20/09/2026

  it('PAID prime sur tout le reste', () => {
    expect(resolvePeriodStatus('p1', '2026-09-15', [tx('p1', 'REJECTED'), tx('p1', 'VALIDATED')], today)).toBe('PAID');
  });

  it('PENDING si un paiement est en attente, même après échéance', () => {
    expect(resolvePeriodStatus('p1', '2026-09-15', [tx('p1', 'PENDING')], today)).toBe('PENDING');
  });

  it("LATE seulement si l'échéance est strictement dépassée", () => {
    expect(resolvePeriodStatus('p1', '2026-09-19', [], today)).toBe('LATE');
    expect(resolvePeriodStatus('p1', '2026-09-20', [], today)).toBe('NOT_PAID');
  });

  it('un paiement rejeté ne compte pas ; sans échéance -> NOT_PAID', () => {
    expect(resolvePeriodStatus('p1', null, [tx('p1', 'REJECTED')], today)).toBe('NOT_PAID');
  });

  it("ignore les paiements d'autres périodes", () => {
    expect(resolvePeriodStatus('p1', null, [tx('p2', 'VALIDATED')], today)).toBe('NOT_PAID');
  });
});

describe('displayedPeriodStatus / canDeclare', () => {
  it("une cotisation facultative n'est jamais affichée en retard", () => {
    expect(displayedPeriodStatus('LATE', false)).toBe('NOT_PAID');
    expect(displayedPeriodStatus('LATE', true)).toBe('LATE');
  });

  it('déclaration possible uniquement sans paiement en attente ou validé', () => {
    expect(canDeclare('NOT_PAID')).toBe(true);
    expect(canDeclare('LATE')).toBe(true);
    expect(canDeclare('PENDING')).toBe(false);
    expect(canDeclare('PAID')).toBe(false);
  });
});
