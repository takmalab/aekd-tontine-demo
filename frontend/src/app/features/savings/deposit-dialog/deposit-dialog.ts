import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { toIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS, PaymentOperator } from '../../contributions/contribution.model';
import { SavingsEntry } from '../savings.model';
import { SavingsService } from '../savings.service';

/**
 * « Épargner » : le membre déclare un versement libre sur son épargne
 * (docs/decisions.md n° 39). Il est compté après validation par le trésorier.
 * Se ferme avec le mouvement créé, ou undefined.
 */
@Component({
  selector: 'app-deposit-dialog',
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
  templateUrl: './deposit-dialog.html',
  styleUrl: './deposit-dialog.scss',
})
export class DepositDialog {
  private readonly fb = inject(FormBuilder);
  private readonly savingsService = inject(SavingsService);
  private readonly dialogRef = inject<MatDialogRef<DepositDialog, SavingsEntry>>(MatDialogRef);

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

  readonly form = this.fb.group({
    amount: [null as number | null, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
    paymentDate: [new Date() as Date | null, Validators.required],
    operator: [null as PaymentOperator | null, Validators.required],
    reference: ['', [Validators.required, Validators.maxLength(150)]],
    observation: ['', Validators.maxLength(500)],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.savingsService
      .declare({
        amount: Number(v.amount),
        paymentDate: toIsoDate(v.paymentDate!),
        operator: v.operator!,
        reference: v.reference!.trim(),
        observation: v.observation?.trim() || null,
      })
      .subscribe({
        next: (entry) => this.dialogRef.close(entry),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400
              ? error.error?.message || 'Les informations saisies sont invalides.'
              : error.status === 403
                ? error.error?.message || "Ce compte n'a pas de profil membre."
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
