import { ContributionTransaction, ContributionTransactionStatus } from '../contributions/contribution.model';
import {
  AmountRule,
  canDeclare,
  displayedPeriodStatus,
  remainingAmount,
  resolvePeriodStatus,
} from './period-status';

const tx = (periodId: string, status: ContributionTransactionStatus, amount = 50000): ContributionTransaction => ({
  id: `${periodId}-${status}-${amount}`,
  memberId: 'm1',
  memberFullName: 'Membre',
  contributionDefinitionId: 'c1',
  contributionDefinitionName: 'Cotisation',
  contributionPeriodId: periodId,
  sessionId: 's1',
  sessionLabel: 'Séance',
  amount,
  operator: 'MTN_MOMO',
  transactionReference: 'REF',
  paymentDate: '2026-09-10',
  observation: null,
  status,
  validatedByEmail: null,
  validatedAt: null,
});

const FIXED: AmountRule = { amountMode: 'FIXED', amount: 50000 };
const VOLUNTARY: AmountRule = { amountMode: 'VOLUNTARY', amount: null };

describe('resolvePeriodStatus (miroir du backend)', () => {
  const today = new Date(2026, 8, 20); // 20/09/2026

  it('PAID prime sur tout le reste', () => {
    expect(resolvePeriodStatus('p1', '2026-09-15', [tx('p1', 'REJECTED'), tx('p1', 'VALIDATED')], FIXED, today)).toBe('PAID');
  });

  it('PENDING si un paiement est en attente, même après échéance', () => {
    expect(resolvePeriodStatus('p1', '2026-09-15', [tx('p1', 'PENDING')], FIXED, today)).toBe('PENDING');
  });

  it("LATE seulement si l'échéance est strictement dépassée", () => {
    expect(resolvePeriodStatus('p1', '2026-09-19', [], FIXED, today)).toBe('LATE');
    expect(resolvePeriodStatus('p1', '2026-09-20', [], FIXED, today)).toBe('NOT_PAID');
  });

  it('un paiement rejeté ne compte pas ; sans échéance -> NOT_PAID', () => {
    expect(resolvePeriodStatus('p1', null, [tx('p1', 'REJECTED')], FIXED, today)).toBe('NOT_PAID');
  });

  it("ignore les paiements d'autres périodes", () => {
    expect(resolvePeriodStatus('p1', null, [tx('p2', 'VALIDATED')], FIXED, today)).toBe('NOT_PAID');
  });
});

describe('paiements partiels (montant fixe)', () => {
  const today = new Date(2026, 8, 20);

  it('PARTIAL quand la somme validée est inférieure au montant, même après échéance', () => {
    const txs = [tx('p1', 'VALIDATED', 20000)];
    expect(resolvePeriodStatus('p1', '2026-09-15', txs, FIXED, today)).toBe('PARTIAL');
    expect(remainingAmount('p1', FIXED, txs)).toBe(30000);
  });

  it('PENDING prime sur PARTIAL quand un complément est en attente', () => {
    const txs = [tx('p1', 'VALIDATED', 20000), tx('p1', 'PENDING', 30000)];
    expect(resolvePeriodStatus('p1', null, txs, FIXED, today)).toBe('PENDING');
  });

  it('PAID quand les versements validés atteignent le montant', () => {
    const txs = [tx('p1', 'VALIDATED', 20000), tx('p1', 'VALIDATED', 30000)];
    expect(resolvePeriodStatus('p1', null, txs, FIXED, today)).toBe('PAID');
    expect(remainingAmount('p1', FIXED, txs)).toBe(0);
  });

  it('montant libre : inchangé (un paiement validé suffit), pas de reste dû', () => {
    const txs = [tx('p1', 'VALIDATED', 1000)];
    expect(resolvePeriodStatus('p1', null, txs, VOLUNTARY, today)).toBe('PAID');
    expect(remainingAmount('p1', VOLUNTARY, txs)).toBeNull();
  });
});

describe('displayedPeriodStatus / canDeclare', () => {
  it("une cotisation facultative n'est jamais affichée en retard", () => {
    expect(displayedPeriodStatus('LATE', false)).toBe('NOT_PAID');
    expect(displayedPeriodStatus('LATE', true)).toBe('LATE');
  });

  it('déclaration possible sans paiement en attente et tant que ce n\'est pas entièrement payé', () => {
    expect(canDeclare('NOT_PAID')).toBe(true);
    expect(canDeclare('LATE')).toBe(true);
    expect(canDeclare('PARTIAL')).toBe(true);
    expect(canDeclare('PENDING')).toBe(false);
    expect(canDeclare('PAID')).toBe(false);
  });
});
