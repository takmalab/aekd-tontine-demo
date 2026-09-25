import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { ContributionTransaction, PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { PaymentService } from '../payment.service';
import { ProofViewerDialog } from '../proof-viewer-dialog/proof-viewer-dialog';

type Decision = 'validate' | 'reject';

/**
 * Paiements en attente (CLAUDE.md §33 étapes 6-7). Consultables par ADMIN et
 * TRESORIER ; seul le TRESORIER peut valider ou rejeter (§6 — l'ADMIN consulte).
 */
@Component({
  selector: 'app-pending-payments',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './pending-payments.html',
  styleUrl: './pending-payments.scss',
})
export class PendingPayments implements OnInit {
  private readonly paymentService = inject(PaymentService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Validation/rejet : TRESORIER uniquement (pas l'ADMIN). */
  readonly canDecide = this.authService.hasRole('TRESORIER');
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly payments = signal<ContributionTransaction[]>([]);
  readonly busyId = signal<string | null>(null);

  readonly total = computed(() => this.payments().reduce((sum, p) => sum + p.amount, 0));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.paymentService.pending().subscribe({
      next: (payments) => {
        // Les plus anciennes déclarations d'abord.
        this.payments.set([...payments].sort((a, b) => a.paymentDate.localeCompare(b.paymentDate)));
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  viewProof(payment: ContributionTransaction): void {
    this.dialog.open(ProofViewerDialog, {
      data: payment,
      width: '760px',
      maxWidth: 'calc(100vw - 24px)',
      autoFocus: false,
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
            message: `${amount} de ${payment.memberFullName} pour « ${payment.contributionDefinitionName} » ne sera pas comptabilisé. Le membre pourra déclarer un nouveau paiement. Cette action est définitive.`,
            confirmLabel: 'Rejeter',
            icon: 'block',
          };

    this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busyId.set(payment.id);
        const call =
          decision === 'validate' ? this.paymentService.validate(payment.id) : this.paymentService.reject(payment.id);
        call.subscribe({
          next: () => {
            this.busyId.set(null);
            this.payments.update((list) => list.filter((p) => p.id !== payment.id));
            this.snackBar.open(
              decision === 'validate' ? 'Paiement validé : fonds mis à jour' : 'Paiement rejeté',
              'OK',
              { duration: 4000 },
            );
          },
          error: (err: HttpErrorResponse) => {
            this.busyId.set(null);
            const message =
              err.status === 400
                ? 'Ce paiement a déjà été traité.'
                : err.status === 403
                  ? 'Action non autorisée (réservée au trésorier).'
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
