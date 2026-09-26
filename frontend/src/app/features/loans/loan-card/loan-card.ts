import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import {
  LOAN_STATUSES_WITH_BALANCE,
  LOAN_STATUS_LABELS,
  Loan,
  LoanBalance,
  LoanRepayment,
} from '../loan.model';
import { LoanService } from '../loan.service';
import { RuleChecklist } from '../rule-checklist/rule-checklist';

/**
 * Carte d'un prêt : montants demandés/approuvés, évaluation des règles, et pour un
 * prêt approuvé le solde (§23 : montant - remboursé = solde restant) et l'historique
 * des remboursements. Les actions sont émises vers la page parente.
 */
@Component({
  selector: 'app-loan-card',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, RuleChecklist],
  templateUrl: './loan-card.html',
  styleUrl: './loan-card.scss',
})
export class LoanCard {
  private readonly loanService = inject(LoanService);

  readonly loan = input.required<Loan>();
  /** Affiche le nom du membre (vue trésorier/admin). */
  readonly showMember = input(false);
  /** Approuver / rejeter (TRESORIER uniquement). */
  readonly canDecide = input(false);
  /** Enregistrer un remboursement (TRESORIER uniquement). */
  readonly canRepay = input(false);

  readonly approve = output<Loan>();
  readonly reject = output<Loan>();
  readonly repay = output<{ loan: Loan; balance: LoanBalance }>();

  readonly statusLabels = LOAN_STATUS_LABELS;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;

  readonly balance = signal<LoanBalance | null>(null);
  readonly repayments = signal<LoanRepayment[]>([]);
  readonly moneyLoading = signal(false);
  readonly moneyError = signal(false);
  readonly rulesOpen = signal(false);
  readonly historyOpen = signal(false);

  readonly hasBalance = computed(() => LOAN_STATUSES_WITH_BALANCE.includes(this.loan().status));
  readonly failedRules = computed(() => this.loan().ruleEvaluations.filter((r) => !r.respected).length);
  readonly repaidPercent = computed(() => {
    const b = this.balance();
    return b && b.totalAmount > 0 ? Math.min(100, (b.totalRepaid / b.totalAmount) * 100) : 0;
  });
  readonly approvedDiffers = computed(() => {
    const l = this.loan();
    return (
      l.approvedAmount !== null &&
      (l.approvedAmount !== l.requestedAmount || l.approvedDurationMonths !== l.requestedDurationMonths)
    );
  });

  constructor() {
    // Les règles sont dépliées par défaut pour une demande à traiter.
    effect(() => this.rulesOpen.set(this.loan().status === 'REQUESTED'));
    // Solde et remboursements rechargés à chaque changement du prêt (ex. après un remboursement).
    effect(() => {
      const loan = this.loan();
      if (LOAN_STATUSES_WITH_BALANCE.includes(loan.status)) {
        this.loadMoney(loan.id);
      }
    });
  }

  loadMoney(loanId = this.loan().id): void {
    this.moneyLoading.set(true);
    this.moneyError.set(false);
    forkJoin({
      balance: this.loanService.balance(loanId),
      repayments: this.loanService.repayments(loanId),
    }).subscribe({
      next: ({ balance, repayments }) => {
        this.balance.set(balance);
        this.repayments.set([...repayments].sort((a, b) => b.paymentDate.localeCompare(a.paymentDate)));
        this.moneyLoading.set(false);
      },
      error: () => {
        this.moneyError.set(true);
        this.moneyLoading.set(false);
      },
    });
  }

  emitRepay(): void {
    const balance = this.balance();
    if (balance) {
      this.repay.emit({ loan: this.loan(), balance });
    }
  }
}
