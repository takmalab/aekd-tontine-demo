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
}

/**
 * Enregistrement d'un remboursement par le trésorier (CLAUDE.md §23). Montant
 * plafonné au solde restant (le backend refuse aussi tout dépassement). Le statut
 * du prêt évolue automatiquement : IN_PROGRESS, puis REPAID quand le solde est nul.
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
  readonly remaining = this.data.balance.remainingBalance;
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
    return value > 0 && value <= this.remaining ? this.remaining - value : null;
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
    this.loanService
      .recordRepayment(this.data.loan.id, {
        amount: Number(v.amount),
        paymentDate: toIsoDate(v.paymentDate!),
        operator: v.operator!,
        reference: v.reference!.trim(),
      })
      .subscribe({
        next: (repayment) => this.dialogRef.close(repayment),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400
              ? `Remboursement refusé : il ne doit pas dépasser le solde restant (${formatFcfa(this.remaining)}) et le prêt doit être en cours.`
              : error.status === 403
                ? 'Action réservée au trésorier.'
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
