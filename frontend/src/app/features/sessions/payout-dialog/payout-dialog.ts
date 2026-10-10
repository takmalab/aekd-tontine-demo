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
import { formatFcfa, toIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS, PaymentOperator } from '../../contributions/contribution.model';
import { PayoutBeneficiaryShare, SessionPayout } from '../session.model';
import { SessionService } from '../session.service';

export interface PayoutDialogData {
  sessionId: string;
  sessionLabel: string;
  share: PayoutBeneficiaryShare;
}

/**
 * Remise de la cagnotte à un bénéficiaire de la séance (docs/decisions.md n° 46).
 * Montant prérempli avec la part restante, qu'il ne peut pas dépasser.
 * Se ferme avec la remise enregistrée, ou undefined.
 */
@Component({
  selector: 'app-payout-dialog',
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
  templateUrl: './payout-dialog.html',
  styleUrl: './payout-dialog.scss',
})
export class PayoutDialog {
  private readonly fb = inject(FormBuilder);
  private readonly sessionService = inject(SessionService);
  private readonly dialogRef = inject<MatDialogRef<PayoutDialog, SessionPayout>>(MatDialogRef);
  readonly data = inject<PayoutDialogData>(MAT_DIALOG_DATA);
  readonly formatFcfa = formatFcfa;

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
    amount: [
      this.data.share.due as number | null,
      [Validators.required, Validators.min(1), Validators.max(this.data.share.due), Validators.pattern(/^\d+$/)],
    ],
    payoutDate: [new Date() as Date | null, Validators.required],
    operator: [null as PaymentOperator | null, Validators.required],
    reference: ['', Validators.maxLength(150)],
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
    this.sessionService
      .recordPayout(this.data.sessionId, {
        memberId: this.data.share.memberId,
        amount: Number(v.amount),
        payoutDate: toIsoDate(v.payoutDate!),
        operator: v.operator!,
        reference: v.reference?.trim() || null,
        observation: v.observation?.trim() || null,
      })
      .subscribe({
        next: (payout) => this.dialogRef.close(payout),
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
