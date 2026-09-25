import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa, fromIsoDate, toIsoDate } from '../../../shared/utils/format';
import {
  ContributionDefinition,
  ContributionPeriod,
  ContributionTransaction,
  PAYMENT_OPERATOR_LABELS,
  PaymentOperator,
} from '../../contributions/contribution.model';
import { PaymentService } from '../payment.service';

export interface DeclarePaymentData {
  definition: ContributionDefinition;
  period: ContributionPeriod;
}

@Component({
  selector: 'app-declare-payment-dialog',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './declare-payment-dialog.html',
  styleUrl: './declare-payment-dialog.scss',
})
export class DeclarePaymentDialog {
  private readonly fb = inject(FormBuilder);
  private readonly paymentService = inject(PaymentService);
  private readonly dialogRef = inject<MatDialogRef<DeclarePaymentDialog, ContributionTransaction>>(MatDialogRef);
  readonly data = inject<DeclarePaymentData>(MAT_DIALOG_DATA);

  readonly isFixed = this.data.definition.amountMode === 'FIXED';
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
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
    // Montant fixe : prérempli et verrouillé (le backend exige qu'il soit égal au montant de la cotisation).
    amount: [
      { value: this.isFixed ? this.data.definition.amount : null, disabled: this.isFixed },
      [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)],
    ],
    operator: [null as PaymentOperator | null, Validators.required],
    transactionReference: ['', [Validators.required, Validators.maxLength(100)]],
    paymentDate: [new Date() as Date | null, Validators.required],
    observation: ['', [Validators.maxLength(500)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.paymentService
      .declare(this.data.definition.id, {
        contributionPeriodId: this.data.period.id,
        amount: Number(v.amount),
        operator: v.operator!,
        transactionReference: v.transactionReference!.trim(),
        paymentDate: toIsoDate(v.paymentDate!),
        observation: v.observation?.trim() || null,
      })
      .subscribe({
        next: (transaction) => this.dialogRef.close(transaction),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 409
              ? 'Un paiement est déjà déclaré ou validé pour cette période.'
              : error.status === 403
                ? "Vous n'êtes pas participant à cette cotisation."
                : error.status === 400
                  ? 'Les informations saisies sont invalides (vérifiez le montant).'
                  : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
