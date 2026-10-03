import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa, toIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS, PaymentOperator } from '../../contributions/contribution.model';
import { Loan, LoanBalance, LoanRepayment } from '../loan.model';
import { LoanService } from '../loan.service';

export interface RepaymentData {
  loan: Loan;
  balance: LoanBalance;
  /**
   * Vrai quand le membre rembourse son propre prêt : le remboursement est déclaré
   * et reste en attente de validation (docs/decisions.md n° 38). Faux (défaut) :
   * le trésorier enregistre un remboursement reçu, comptabilisé immédiatement.
   */
  declare?: boolean;
}

/**
 * Remboursement d'un prêt (CLAUDE.md §23), partiel ou total. Montant plafonné au
 * solde restant, diminué des remboursements déjà en attente pour une déclaration
 * (le backend applique la même règle). Le statut du prêt évolue quand le
 * remboursement est comptabilisé : IN_PROGRESS, puis REPAID quand le solde est nul.
 */
@Component({
  selector: 'app-repayment-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './repayment-dialog.html',
  styleUrl: './repayment-dialog.scss',
})
export class RepaymentDialog {
  private readonly fb = inject(FormBuilder);
  private readonly loanService = inject(LoanService);
  private readonly dialogRef = inject<MatDialogRef<RepaymentDialog, LoanRepayment>>(MatDialogRef);
  readonly data = inject<RepaymentData>(MAT_DIALOG_DATA);

  readonly formatFcfa = formatFcfa;
  readonly declaring = this.data.declare === true;
  /** Montant encore remboursable : solde restant, moins ce qui est déjà déclaré et en attente. */
  readonly remaining = this.declaring
    ? Math.max(0, this.data.balance.remainingBalance - (this.data.balance.pendingAmount ?? 0))
    : this.data.balance.remainingBalance;
  readonly operators = Object.entries(PAYMENT_OPERATOR_LABELS) as [PaymentOperator, string][];
  readonly operatorIcons: Record<PaymentOperator, string> = {
    MTN_MOMO: 'smartphone',
    ORANGE_MONEY: 'smartphone',
    BANK_TRANSFER: 'account_balance',
    CASH: 'payments',
  };

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    amount: [
      null as number | null,
      [Validators.required, Validators.min(1), Validators.max(this.remaining), Validators.pattern(/^\d+$/)],
    ],
    paymentDate: [new Date() as Date | null, Validators.required],
    operator: [null as PaymentOperator | null, Validators.required],
    reference: ['', [Validators.required, Validators.maxLength(100)]],
  });

  private readonly amount = toSignal(this.form.controls.amount.valueChanges, { initialValue: null });

  /** Solde après ce remboursement (aperçu, si le montant saisi est valide). */
  readonly remainingAfter = computed(() => {
    const value = Number(this.amount());
    return value > 0 && value <= this.remaining
      ? this.data.balance.remainingBalance - (this.declaring ? (this.data.balance.pendingAmount ?? 0) : 0) - value
      : null;
  });

  fillRemaining(): void {
    this.form.controls.amount.setValue(this.remaining);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    const request = {
      amount: Number(v.amount),
      paymentDate: toIsoDate(v.paymentDate!),
      operator: v.operator!,
      reference: v.reference!.trim(),
    };
    (this.declaring
      ? this.loanService.declareRepayment(this.data.loan.id, request)
      : this.loanService.recordRepayment(this.data.loan.id, request)
    ).subscribe({
        next: (repayment) => this.dialogRef.close(repayment),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400
              ? error.error?.message ||
                  `Remboursement refusé : il ne doit pas dépasser ${formatFcfa(this.remaining)} et le prêt doit être en cours.`
              : error.status === 403
                ? 'Action réservée au trésorier.'
                : error.status === 404
                  ? 'Ce prêt est introuvable.'
                  : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
