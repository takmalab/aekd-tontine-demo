import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { DocumentService } from '../../../core/services/document.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { ContributionTransaction, PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { PaymentService } from '../payment.service';
import { LoanRepayment } from '../../loans/loan.model';
import { LoanService } from '../../loans/loan.service';
import { SavingsService } from '../../savings/savings.service';
import { ProofViewerDialog } from '../proof-viewer-dialog/proof-viewer-dialog';

type Decision = 'validate' | 'reject';

/**
 * Paiements de cotisations (CLAUDE.md §33 étapes 6-7) et remboursements de prêts
 * (docs/decisions.md n° 38) déclarés par les membres, en attente. Consultables par
 * ADMIN et TRESORIER ; seul le TRESORIER peut valider ou rejeter (§6).
 */
@Component({
  selector: 'app-pending-payments',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, ExportMenu],
  templateUrl: './pending-payments.html',
  styleUrl: './pending-payments.scss',
})
export class PendingPayments implements OnInit {
  private readonly paymentService = inject(PaymentService);
  private readonly loanService = inject(LoanService);
  private readonly savingsService = inject(SavingsService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Validation/rejet : TRESORIER et ADMIN (docs/decisions.md n° 40). */
  readonly canDecide = this.authService.hasRole('TRESORIER');
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly payments = signal<ContributionTransaction[]>([]);
  readonly repayments = signal<LoanRepayment[]>([]);
  /** Versements d'épargne en attente : validés sur la page « Épargnes ». */
  readonly pendingSavings = signal(0);
  readonly busyId = signal<string | null>(null);

  readonly total = computed(() => this.payments().reduce((sum, p) => sum + p.amount, 0));
  readonly repaymentsTotal = computed(() => this.repayments().reduce((sum, r) => sum + r.amount, 0));

  private readonly documents = inject(DocumentService);

  readonly exportRows = computed(() =>
    this.payments().map((p) => ({
      Membre: p.memberFullName,
      Cotisation: p.contributionDefinitionName,
      Séance: p.sessionLabel,
      Montant: formatFcfa(p.amount),
      Opérateur: this.operatorLabels[p.operator],
      Référence: p.transactionReference,
      'Payé le': p.paymentDate,
    })),
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    forkJoin({ payments: this.paymentService.pending(), repayments: this.loanService.pendingRepayments() }).subscribe({
      next: ({ payments, repayments }) => {
        // Les plus anciennes déclarations d'abord.
        this.payments.set([...payments].sort((a, b) => a.paymentDate.localeCompare(b.paymentDate)));
        this.repayments.set([...repayments].sort((a, b) => a.paymentDate.localeCompare(b.paymentDate)));
        this.loading.set(false);
        this.savingsService.pending().subscribe({
          next: (deposits) => this.pendingSavings.set(deposits.length),
          error: () => this.pendingSavings.set(0),
        });
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  viewProof(payment: ContributionTransaction): void {
    this.dialog.open(ProofViewerDialog, {
      data: { transaction: payment },
      width: '760px',
      maxWidth: 'calc(100vw - 24px)',
      autoFocus: false,
    });
  }

  viewRepaymentProof(repayment: LoanRepayment): void {
    this.dialog.open(ProofViewerDialog, {
      data: { repayment },
      width: '760px',
      maxWidth: 'calc(100vw - 24px)',
      autoFocus: false,
    });
  }

  /** Seul un remboursement validé réduit le solde du prêt (docs/decisions.md n° 38). */
  decideRepayment(repayment: LoanRepayment, decision: Decision): void {
    const amount = formatFcfa(repayment.amount);
    const data: ConfirmDialogData =
      decision === 'validate'
        ? {
            title: 'Valider ce remboursement ?',
            message: `${amount} de ${repayment.memberFullName} sera considéré comme encaissé et déduit du solde de son prêt. Cette action est définitive.`,
            confirmLabel: 'Valider',
            icon: 'task_alt',
          }
        : {
            title: 'Rejeter ce remboursement ?',
            message: `Le remboursement de ${amount} déclaré par ${repayment.memberFullName} ne sera pas déduit du solde. Le membre pourra en déclarer un nouveau.`,
            confirmLabel: 'Rejeter',
            icon: 'block',
          };
    this.dialog
      .open(ConfirmDialog, { data, width: '440px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busyId.set(repayment.id);
        const call =
          decision === 'validate'
            ? this.loanService.validateRepayment(repayment.id)
            : this.loanService.rejectRepayment(repayment.id);
        call.subscribe({
          next: () => {
            this.busyId.set(null);
            this.repayments.update((list) => list.filter((r) => r.id !== repayment.id));
            if (decision === 'validate') {
              this.snackBar
                .open('Remboursement validé : solde du prêt mis à jour', 'Reçu PDF', { duration: 8000 })
                .onAction()
                .subscribe(() => this.documents.repaymentReceipt(repayment.id));
            } else {
              this.snackBar.open('Remboursement rejeté', 'OK', { duration: 4000 });
            }
          },
          error: (err: HttpErrorResponse) => {
            this.busyId.set(null);
            this.snackBar.open(
              err.status === 400 || err.status === 403
                ? err.error?.message || "L'opération a été refusée."
                : "L'opération a échoué. Vérifiez que le serveur est démarré.",
              'OK',
              { duration: 7000 },
            );
            if (err.status === 400) {
              this.load();
            }
          },
        });
      });
  }

  decide(payment: ContributionTransaction, decision: Decision): void {
    const amount = formatFcfa(payment.amount);
    const data: ConfirmDialogData =
      decision === 'validate'
        ? {
            title: 'Valider ce paiement ?',
            message: `${amount} de ${payment.memberFullName} pour « ${payment.contributionDefinitionName} » sera considéré comme encaissé et comptabilisé dans les fonds. Cette action est définitive.`,
            confirmLabel: 'Valider',
            icon: 'task_alt',
          }
        : {
            title: 'Rejeter ce paiement ?',
            message: `${amount} de ${payment.memberFullName} pour « ${payment.contributionDefinitionName} » ne sera pas comptabilisé. Le membre verra le motif et pourra déclarer un nouveau paiement. Cette action est définitive.`,
            confirmLabel: 'Rejeter',
            icon: 'block',
            reasonLabel: 'Motif du rejet (visible par le membre)',
          };

    this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed?: true | string) => {
        if (!confirmed) {
          return;
        }
        this.busyId.set(payment.id);
        const call =
          decision === 'validate'
            ? this.paymentService.validate(payment.id)
            : this.paymentService.reject(payment.id, String(confirmed));
        call.subscribe({
          next: () => {
            this.busyId.set(null);
            this.payments.update((list) => list.filter((p) => p.id !== payment.id));
            if (decision === 'validate') {
              this.snackBar
                .open('Paiement validé : fonds mis à jour', 'Reçu PDF', { duration: 8000 })
                .onAction()
                .subscribe(() => this.documents.paymentReceipt(payment.id));
            } else {
              this.snackBar.open('Paiement rejeté', 'OK', { duration: 4000 });
            }
          },
          error: (err: HttpErrorResponse) => {
            this.busyId.set(null);
            const message =
              err.status === 400
                ? 'Ce paiement a déjà été traité.'
                : err.status === 403
                  ? "Action non autorisée (réservée au trésorier et à l'administrateur)."
                  : "L'opération a échoué. Vérifiez que le serveur est démarré.";
            this.snackBar.open(message, 'OK', { duration: 6000 });
            if (err.status === 400) {
              this.load();
            }
          },
        });
      });
  }
}
