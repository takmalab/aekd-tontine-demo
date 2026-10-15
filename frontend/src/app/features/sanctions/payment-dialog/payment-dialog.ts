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
import { switchMap, of } from 'rxjs';
import { formatFcfa, toIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS, PaymentOperator } from '../../contributions/contribution.model';
import { AppliedSanction, SanctionPayment, sanctionRemaining } from '../sanction.model';
import { SanctionService } from '../sanction.service';

/**
 * « Payer » une sanction monétaire (decisions.md n° 50) : le membre déclare son
 * paiement, éventuellement partiel ; il entre au compte Sanctions après
 * validation par le trésorier. Se ferme avec le paiement créé, ou undefined.
 */
@Component({
  selector: 'app-sanction-payment-dialog',
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
  templateUrl: './payment-dialog.html',
  styleUrl: './payment-dialog.scss',
})
export class SanctionPaymentDialog {
  private readonly fb = inject(FormBuilder);
  private readonly sanctionService = inject(SanctionService);
  private readonly dialogRef = inject<MatDialogRef<SanctionPaymentDialog, SanctionPayment>>(MatDialogRef);
  readonly sanction = inject<AppliedSanction>(MAT_DIALOG_DATA);

  readonly formatFcfa = formatFcfa;
  readonly remaining = sanctionRemaining(this.sanction);
  readonly operators = Object.entries(PAYMENT_OPERATOR_LABELS) as [PaymentOperator, string][];
  readonly operatorIcons: Record<PaymentOperator, string> = {
    MTN_MOMO: 'smartphone',
    ORANGE_MONEY: 'smartphone',
    BANK_TRANSFER: 'account_balance',
    CASH: 'payments',
  };
  readonly today = new Date();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly proof = signal<File | null>(null);

  readonly form = this.fb.group({
    amount: [
      this.remaining as number | null,
      [Validators.required, Validators.min(1), Validators.max(this.remaining), Validators.pattern(/^\d+$/)],
    ],
    paymentDate: [new Date() as Date | null, Validators.required],
    operator: [null as PaymentOperator | null, Validators.required],
    reference: ['', [Validators.required, Validators.maxLength(150)]],
    observation: ['', Validators.maxLength(500)],
  });

  pickProof(event: Event): void {
    this.proof.set((event.target as HTMLInputElement).files?.[0] ?? null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.sanctionService
      .declarePayment(this.sanction.id, {
        amount: Number(v.amount),
        paymentDate: toIsoDate(v.paymentDate!),
        operator: v.operator!,
        reference: v.reference!.trim(),
        observation: v.observation?.trim() || null,
      })
      .pipe(
        switchMap((payment) => {
          const file = this.proof();
          return file ? this.sanctionService.uploadPaymentProof(payment.id, file) : of(payment);
        }),
      )
      .subscribe({
        next: (payment) => this.dialogRef.close(payment),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400 || error.status === 403
              ? error.error?.message || 'Les informations saisies sont invalides.'
              : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
