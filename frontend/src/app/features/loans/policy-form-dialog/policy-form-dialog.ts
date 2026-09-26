import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LoanPolicy } from '../loan.model';
import { LoanService } from '../loan.service';

/** Le montant minimum ne peut pas dépasser le maximum (même contrôle que le backend). */
function minMaxValidator(group: AbstractControl): ValidationErrors | null {
  const min = group.get('minAmount')?.value;
  const max = group.get('maxAmount')?.value;
  return min !== null && min !== '' && max !== null && max !== '' && Number(min) > Number(max)
    ? { minAboveMax: true }
    : null;
}

const positiveInt = [Validators.min(1), Validators.pattern(/^\d+$/)];

/**
 * Création d'une politique de prêt (CLAUDE.md §20) — ADMIN uniquement.
 * Tous les critères sont facultatifs : aucun seuil n'est imposé par défaut
 * (les valeurs réelles restent une décision à valider, §38).
 */
@Component({
  selector: 'app-policy-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './policy-form-dialog.html',
  styleUrl: './policy-form-dialog.scss',
})
export class PolicyFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly loanService = inject(LoanService);
  private readonly dialogRef = inject<MatDialogRef<PolicyFormDialog, LoanPolicy>>(MatDialogRef);

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group(
    {
      name: ['', [Validators.required, Validators.maxLength(150)]],
      description: ['', [Validators.maxLength(1000)]],
      minAmount: [null as number | null, positiveInt],
      maxAmount: [null as number | null, positiveInt],
      maxDurationMonths: [null as number | null, positiveInt],
      interestRate: [null as number | null, [Validators.min(0)]],
      minSavingsRequired: [null as number | null, positiveInt],
      minSeniorityMonths: [null as number | null, [Validators.min(0), Validators.pattern(/^\d+$/)]],
      maxActiveLoans: [null as number | null, positiveInt],
    },
    { validators: minMaxValidator },
  );

  private num(value: number | string | null | undefined): number | null {
    return value === null || value === undefined || value === '' ? null : Number(value);
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
      .createPolicy({
        name: v.name!.trim(),
        description: v.description?.trim() || null,
        minAmount: this.num(v.minAmount),
        maxAmount: this.num(v.maxAmount),
        maxDurationMonths: this.num(v.maxDurationMonths),
        interestRate: this.num(v.interestRate),
        minSavingsRequired: this.num(v.minSavingsRequired),
        minSeniorityMonths: this.num(v.minSeniorityMonths),
        maxActiveLoans: this.num(v.maxActiveLoans),
      })
      .subscribe({
        next: (policy) => this.dialogRef.close(policy),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400
              ? 'Informations invalides (montants positifs, minimum inférieur au maximum).'
              : error.status === 403
                ? "Action réservée à l'administrateur."
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
