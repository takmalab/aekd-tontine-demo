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
import { PayoutBeneficiaryShare, PayoutDeductionOption, SessionPayout } from '../session.model';
import { SessionService } from '../session.service';

export interface PayoutDialogData {
  sessionId: string;
  sessionLabel: string;
  share: PayoutBeneficiaryShare;
}

/** Retenue proposée sur la cagnotte pour un prêt (decisions.md n° 53). */
interface DeductionLine {
  option: PayoutDeductionOption;
  amount: number;
}

/**
 * Remise de la cagnotte à un bénéficiaire de la séance (docs/decisions.md n° 46).
 * Montant prérempli avec la part restante, qu'il ne peut pas dépasser.
 * Si le bénéficiaire a un prêt en cours, ou garantit un prêt dont l'emprunteur n'a
 * plus d'épargne, le reste à rembourser est retenu sur la cagnotte (n° 53) :
 * prérempli, modifiable par le trésorier.
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

  /** Retenues possibles (prêt du bénéficiaire, puis prêts qu'il garantit). */
  readonly lines = signal<DeductionLine[]>([]);
  readonly blocked = signal<PayoutDeductionOption[]>([]);
  /** Le trésorier a modifié une retenue : on ne recalcule plus quand le montant change. */
  private edited = false;

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

  private readonly amount = toSignal(this.form.controls.amount.valueChanges, {
    initialValue: this.form.controls.amount.value,
  });
  readonly deducted = computed(() => this.lines().reduce((sum, l) => sum + (l.amount || 0), 0));
  readonly handed = computed(() => Math.max(0, Number(this.amount() ?? 0) - this.deducted()));

  constructor() {
    this.sessionService.payoutDeductionOptions(this.data.sessionId, this.data.share.memberId).subscribe({
      next: (options) => {
        this.blocked.set(options.filter((o) => o.blockedReason));
        this.lines.set(options.filter((o) => !o.blockedReason).map((option) => ({ option, amount: 0 })));
        this.autofill();
      },
      error: () => this.errorMessage.set('Impossible de vérifier les prêts du bénéficiaire.'),
    });
    this.form.controls.amount.valueChanges.subscribe(() => {
      if (!this.edited) {
        this.autofill();
      }
    });
  }

  /** Remplit les retenues dans l'ordre (son prêt d'abord), sans dépasser le montant remis. */
  private autofill(): void {
    let budget = Number(this.form.controls.amount.value ?? 0);
    this.lines.update((lines) =>
      lines.map((line) => {
        const amount = Math.max(0, Math.min(line.option.outstanding, budget));
        budget -= amount;
        return { ...line, amount };
      }),
    );
  }

  setLineAmount(loanId: string, event: Event): void {
    this.edited = true;
    const value = Math.max(0, Math.floor(Number((event.target as HTMLInputElement).value) || 0));
    this.lines.update((lines) => lines.map((l) => (l.option.loanId === loanId ? { ...l, amount: value } : l)));
  }

  readonly linesError = computed(() => {
    if (this.lines().some((l) => l.amount > l.option.outstanding)) {
      return 'Une retenue dépasse ce qui reste à rembourser sur le prêt.';
    }
    if (this.deducted() > Number(this.amount() ?? 0)) {
      return 'Les retenues dépassent le montant de la remise.';
    }
    return null;
  });

  submit(): void {
    if (this.form.invalid || this.linesError()) {
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
        deductions: this.lines()
          .filter((l) => l.amount > 0)
          .map((l) => ({ loanId: l.option.loanId, amount: l.amount })),
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
