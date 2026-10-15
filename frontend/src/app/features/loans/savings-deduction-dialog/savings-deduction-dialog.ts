import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { formatFcfa } from '../../../shared/utils/format';
import { Loan, LoanRepayment } from '../loan.model';
import { LoanService } from '../loan.service';

export interface SavingsDeductionData {
  loan: Loan;
  /** Ce qui reste à recouvrer : solde restant moins les remboursements en attente. */
  outstanding: number;
}

/**
 * Prélèvement sur l'épargne pour rembourser un prêt en retard (decisions.md
 * n° 53) : l'emprunteur d'abord ; l'avaliste seulement quand l'épargne de
 * l'emprunteur est épuisée (le serveur le vérifie et explique un refus).
 */
@Component({
  selector: 'app-savings-deduction-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="head">
      <span class="head__icon head__icon--brown"><mat-icon>savings</mat-icon></span>
      <div class="head__text">
        <h2 mat-dialog-title class="head__title">Prélever sur l'épargne</h2>
        <p class="head__subtitle">Prêt de {{ data.loan.memberFullName }} · reste {{ formatFcfa(data.outstanding) }}</p>
      </div>
    </div>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="body">
        <p class="info">
          <mat-icon>info</mat-icon>
          <span>
            Le prêt est en retard. L'épargne de l'<strong>emprunteur</strong> est prélevée d'abord ; celle de
            l'avaliste seulement quand elle est épuisée. Le prélèvement compte comme un remboursement.
          </span>
        </p>
        <mat-radio-group formControlName="payer" class="payers" aria-label="Épargne prélevée">
          <mat-radio-button value="BORROWER">Emprunteur : {{ data.loan.memberFullName }}</mat-radio-button>
          @if (data.loan.guarantorStatus === 'ACCEPTED') {
            <mat-radio-button value="GUARANTOR">Avaliste : {{ data.loan.guarantorFullName }}</mat-radio-button>
          }
        </mat-radio-group>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Montant prélevé</mat-label>
          <input matInput type="number" inputmode="numeric" min="1" step="1" formControlName="amount" />
          <span matTextSuffix>FCFA</span>
          @if (form.controls.amount.hasError('max')) {
            <mat-error>Au plus {{ formatFcfa(data.outstanding) }}</mat-error>
          } @else if (form.controls.amount.invalid) {
            <mat-error>Montant entier strictement positif</mat-error>
          }
        </mat-form-field>
        @if (errorMessage(); as message) {
          <p class="notice" role="alert">
            <mat-icon>error_outline</mat-icon>
            <span>{{ message }}</span>
          </p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end" class="actions">
        <button mat-button type="button" mat-dialog-close [disabled]="saving()">Annuler</button>
        <button mat-flat-button type="submit" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="20" />
          } @else {
            Prélever
          }
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styleUrl: '../loan-request-dialog/loan-request-dialog.scss',
  styles: `
    .payers {
      display: grid;
      gap: 6px;
      margin: 0 0 12px;
    }
  `,
})
export class SavingsDeductionDialog {
  private readonly fb = inject(FormBuilder);
  private readonly loanService = inject(LoanService);
  private readonly dialogRef = inject<MatDialogRef<SavingsDeductionDialog, LoanRepayment>>(MatDialogRef);
  readonly data = inject<SavingsDeductionData>(MAT_DIALOG_DATA);
  readonly formatFcfa = formatFcfa;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    payer: ['BORROWER' as 'BORROWER' | 'GUARANTOR'],
    amount: [
      this.data.outstanding as number | null,
      [Validators.required, Validators.min(1), Validators.max(this.data.outstanding), Validators.pattern(/^\d+$/)],
    ],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.loanService.deductFromSavings(this.data.loan.id, { payer: v.payer!, amount: Number(v.amount) }).subscribe({
      next: (repayment) => this.dialogRef.close(repayment),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(error.error?.message || 'Le prélèvement a échoué.');
      },
    });
  }
}
