import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { DocumentService } from '../../../core/services/document.service';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { ProofUploadData, ProofUploadDialog } from '../../payments/proof-upload-dialog/proof-upload-dialog';
import { ProofViewerData, ProofViewerDialog } from '../../payments/proof-viewer-dialog/proof-viewer-dialog';
import {
  LOAN_STATUSES_WITH_BALANCE,
  LOAN_STATUS_LABELS,
  REPAYMENT_STATUS_LABELS,
  Loan,
  LoanBalance,
  LoanRepayment,
} from '../loan.model';
import { LoanService } from '../loan.service';
import { RepaymentData, RepaymentDialog } from '../repayment-dialog/repayment-dialog';
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
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  readonly documents = inject(DocumentService);

  readonly loan = input.required<Loan>();
  /** Affiche le nom du membre (vue trésorier/admin). */
  readonly showMember = input(false);
  /** Approuver / rejeter (TRESORIER uniquement). */
  readonly canDecide = input(false);
  /** Enregistrer un remboursement reçu (TRESORIER uniquement). */
  readonly canRepay = input(false);
  /**
   * Vue du membre emprunteur : bouton « Rembourser » (déclaration en attente de
   * validation, docs/decisions.md n° 38) et justificatif de ses remboursements.
   */
  readonly canDeclare = input(false);

  readonly approve = output<Loan>();
  readonly reject = output<Loan>();
  readonly repay = output<{ loan: Loan; balance: LoanBalance }>();

  readonly statusLabels = LOAN_STATUS_LABELS;
  readonly repaymentStatusLabels = REPAYMENT_STATUS_LABELS;
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
  readonly repayable = computed(() => this.loan().status === 'APPROVED' || this.loan().status === 'IN_PROGRESS');
  /** Montant encore déclarable par le membre : solde restant moins ce qui est en attente. */
  readonly declarable = computed(() => {
    const b = this.balance();
    return b ? Math.max(0, b.remainingBalance - b.pendingAmount) : 0;
  });
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
        // Un remboursement en attente est mis en évidence : historique déplié.
        if (repayments.some((r) => r.status === 'PENDING')) {
          this.historyOpen.set(true);
        }
        this.moneyLoading.set(false);
      },
      error: () => {
        this.moneyError.set(true);
        this.moneyLoading.set(false);
      },
    });
  }

  /** Le membre rembourse (partiellement ou totalement) son prêt, puis peut joindre un justificatif. */
  declareRepayment(): void {
    const balance = this.balance();
    if (!balance) {
      return;
    }
    const data: RepaymentData = { loan: this.loan(), balance, declare: true };
    this.dialog
      .open(RepaymentDialog, { data, width: '580px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((repayment) => {
        if (!repayment) {
          return;
        }
        this.loadMoney();
        this.attachProof(repayment, true);
      });
  }

  attachProof(repayment: LoanRepayment, justDeclared = false): void {
    const data: ProofUploadData = { repayment, justDeclared };
    this.dialog
      .open(ProofUploadDialog, { data, width: '520px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.loadMoney();
        }
        this.snackBar.open(
          result
            ? 'Justificatif envoyé'
            : justDeclared
              ? 'Remboursement déclaré, en attente de validation par le trésorier'
              : 'Aucun justificatif envoyé',
          'OK',
          { duration: 4000 },
        );
      });
  }

  viewProof(repayment: LoanRepayment): void {
    const data: ProofViewerData = { repayment };
    this.dialog.open(ProofViewerDialog, { data, width: '760px', maxWidth: 'calc(100vw - 24px)', autoFocus: false });
  }

  emitRepay(): void {
    const balance = this.balance();
    if (balance) {
      this.repay.emit({ loan: this.loan(), balance });
    }
  }
}
