import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa } from '../../../shared/utils/format';
import { LoanCost } from '../loan-cost/loan-cost';
import { Loan } from '../loan.model';
import { LoanService } from '../loan.service';
import { RuleChecklist } from '../rule-checklist/rule-checklist';

export interface LoanDecisionData {
  loan: Loan;
  decision: 'approve' | 'reject';
}

/**
 * Décision du trésorier sur une demande de prêt (CLAUDE.md §21) : approbation
 * (montant et durée approuvés, préremplis avec la demande, modifiables) ou rejet
 * (motif obligatoire). Action définitive : la demande n'est plus modifiable ensuite.
 */
@Component({
  selector: 'app-loan-decision-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RuleChecklist,
    LoanCost,
  ],
  templateUrl: './loan-decision-dialog.html',
  styleUrl: './loan-decision-dialog.scss',
})
export class LoanDecisionDialog {
  private readonly fb = inject(FormBuilder);
  private readonly loanService = inject(LoanService);
  private readonly dialogRef = inject<MatDialogRef<LoanDecisionDialog, Loan>>(MatDialogRef);
  readonly data = inject<LoanDecisionData>(MAT_DIALOG_DATA);
  readonly formatFcfa = formatFcfa;
  readonly approving = this.data.decision === 'approve';

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  /** Argent d'épargne disponible pour prêter (null tant qu'il n'est pas chargé). */
  readonly available = signal<number | null>(null);

  constructor() {
    if (this.approving) {
      this.loanService.availableForLending().subscribe({
        next: ({ balance }) => this.available.set(balance),
        error: () => this.available.set(null),
      });
    }
  }

  exceedsAvailable(): boolean {
    const available = this.available();
    const amount = Number(this.approveForm.controls.approvedAmount.value);
    return available !== null && amount > available;
  }

  readonly approveForm = this.fb.group({
    approvedAmount: [
      this.data.loan.requestedAmount as number | null,
      [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)],
    ],
    approvedDurationMonths: [
      this.data.loan.requestedDurationMonths as number | null,
      [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)],
    ],
  });

  /** Montant et durée accordés : les intérêts se recalculent pendant la saisie. */
  readonly approveValues = toSignal(this.approveForm.valueChanges, { initialValue: this.approveForm.getRawValue() });

  readonly rejectForm = this.fb.group({
    rejectionReason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  submit(): void {
    const form = this.approving ? this.approveForm : this.rejectForm;
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);
    const id = this.data.loan.id;
    const call = this.approving
      ? this.loanService.approve(id, {
          approvedAmount: Number(this.approveForm.controls.approvedAmount.value),
          approvedDurationMonths: Number(this.approveForm.controls.approvedDurationMonths.value),
        })
      : this.loanService.reject(id, this.rejectForm.controls.rejectionReason.value!.trim());

    call.subscribe({
      next: (loan) => this.dialogRef.close(loan),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(
          error.status === 400
            ? "Ce prêt n'est plus en attente de décision, ou les informations sont invalides."
            : error.status === 403 || error.status === 409
              ? error.error?.message || "Action réservée au trésorier et à l'administrateur."
              : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }
}
