import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
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
 * Création ou modification d'une politique de prêt (CLAUDE.md §20) — ADMIN
 * uniquement. Tous les critères sont facultatifs : aucun seuil n'est imposé
 * par défaut (les valeurs réelles restent une décision à valider, §38).
 *
 * En mode modification (`data` fourni), le backend refuse la requête (409)
 * si la politique a déjà été utilisée par un prêt, pour ne jamais changer
 * rétroactivement le sens d'une évaluation déjà effectuée.
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
  /** Politique à modifier, ou `null` pour une création. */
  readonly editing = inject<LoanPolicy | null>(MAT_DIALOG_DATA, { optional: true });

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group(
    {
      name: [this.editing?.name ?? '', [Validators.required, Validators.maxLength(150)]],
      description: [this.editing?.description ?? '', [Validators.maxLength(1000)]],
      minAmount: [this.editing?.minAmount ?? (null as number | null), positiveInt],
      maxAmount: [this.editing?.maxAmount ?? (null as number | null), positiveInt],
      maxDurationMonths: [this.editing?.maxDurationMonths ?? (null as number | null), positiveInt],
      interestRate: [this.editing?.interestRate ?? (null as number | null), [Validators.min(0)]],
      minSavingsRequired: [this.editing?.minSavingsRequired ?? (null as number | null), positiveInt],
      minSeniorityMonths: [
        this.editing?.minSeniorityMonths ?? (null as number | null),
        [Validators.min(0), Validators.pattern(/^\d+$/)],
      ],
      maxActiveLoans: [this.editing?.maxActiveLoans ?? (null as number | null), positiveInt],
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
    const request = {
      name: v.name!.trim(),
      description: v.description?.trim() || null,
      minAmount: this.num(v.minAmount),
      maxAmount: this.num(v.maxAmount),
      maxDurationMonths: this.num(v.maxDurationMonths),
      interestRate: this.num(v.interestRate),
      minSavingsRequired: this.num(v.minSavingsRequired),
      minSeniorityMonths: this.num(v.minSeniorityMonths),
      maxActiveLoans: this.num(v.maxActiveLoans),
    };
    const call = this.editing
      ? this.loanService.updatePolicy(this.editing.id, request)
      : this.loanService.createPolicy(request);
    call.subscribe({
      next: (policy) => this.dialogRef.close(policy),
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.errorMessage.set(
          error.status === 400
            ? 'Informations invalides (montants positifs, minimum inférieur au maximum).'
            : error.status === 403
              ? "Action réservée à l'administrateur."
              : error.status === 409
                ? (error.error?.message ?? 'Cette politique a déjà été utilisée par un prêt : elle ne peut plus être modifiée.')
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }
}
