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
  /**
   * Reste dû pour un montant fixe (paiements partiels autorisés) ; null ou absent
   * pour un montant libre. Par défaut : le montant complet de la cotisation.
   */
  remaining?: number | null;
  /**
   * Décision n° 65 : présent quand le trésorier enregistre le paiement d'un membre (ex. espèces
   * remises à la séance). Le paiement est alors validé d'emblée ; la référence devient facultative.
   */
  member?: { id: string; fullName: string };
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
  /** Plafond du montant déclarable (montant fixe) : le reste dû. */
  readonly remaining: number | null = this.isFixed
    ? (this.data.remaining ?? this.data.definition.amount)
    : null;
  readonly isComplement = this.isFixed && this.remaining !== null && this.remaining < (this.data.definition.amount ?? 0);
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
    // Montant fixe : prérempli avec le reste dû, modifiable à la baisse (paiement partiel),
    // plafonné au reste dû (même contrôle que le backend).
    amount: [
      this.remaining as number | null,
      [
        Validators.required,
        Validators.min(1),
        Validators.pattern(/^\d+$/),
        ...(this.remaining !== null ? [Validators.max(this.remaining)] : []),
      ],
    ],
    // Enregistrement par le trésorier : espèces par défaut, référence facultative.
    operator: [(this.data.member ? 'CASH' : null) as PaymentOperator | null, Validators.required],
    transactionReference: ['', this.data.member ? [Validators.maxLength(100)] : [Validators.required, Validators.maxLength(100)]],
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
    const member = this.data.member;
    const save$ = member
      ? this.paymentService.record(this.data.definition.id, {
          memberId: member.id,
          contributionPeriodId: this.data.period.id,
          amount: Number(v.amount),
          operator: v.operator!,
          transactionReference: v.transactionReference?.trim() || null,
          paymentDate: toIsoDate(v.paymentDate!),
          observation: v.observation?.trim() || null,
        })
      : this.paymentService.declare(this.data.definition.id, {
          contributionPeriodId: this.data.period.id,
          amount: Number(v.amount),
          operator: v.operator!,
          transactionReference: v.transactionReference!.trim(),
          paymentDate: toIsoDate(v.paymentDate!),
          observation: v.observation?.trim() || null,
        });
    save$.subscribe({
        next: (transaction) => this.dialogRef.close(transaction),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          if (member && error.error?.message && error.status >= 400 && error.status < 500) {
            // Messages métier du serveur : non participant, propre paiement, reste dû…
            this.errorMessage.set(error.error.message);
            return;
          }
          this.errorMessage.set(
            error.status === 409
              ? 'Un paiement est déjà en attente ou la cotisation est déjà réglée pour cette période.'
              : error.status === 403
                ? "Vous n'êtes pas participant à cette cotisation."
                : error.status === 400
                  ? this.remaining !== null
                    ? `Montant invalide : il doit être positif et ne pas dépasser le reste dû (${formatFcfa(this.remaining)}).`
                    : 'Les informations saisies sont invalides (vérifiez le montant).'
                  : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
