import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ContributionDefinition } from '../../contributions/contribution.model';
import { SanctionRule, SanctionType } from '../sanction.model';
import { SanctionService } from '../sanction.service';

export interface RuleFormData {
  contribution: ContributionDefinition;
}

/**
 * Création d'une règle de sanction (CLAUDE.md §18) pour une cotisation obligatoire
 * — ADMIN et TRESORIER. Les seuils et montants ne sont pas prédéfinis : ce sont des
 * décisions à valider (§38), saisies par l'utilisateur.
 */
@Component({
  selector: 'app-rule-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './rule-form-dialog.html',
  styleUrl: './rule-form-dialog.scss',
})
export class RuleFormDialog {
  private readonly fb = inject(FormBuilder);
  private readonly sanctionService = inject(SanctionService);
  private readonly dialogRef = inject<MatDialogRef<RuleFormDialog, SanctionRule>>(MatDialogRef);
  readonly data = inject<RuleFormData>(MAT_DIALOG_DATA);

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    type: ['MONETARY' as SanctionType, Validators.required],
    lateDaysThreshold: [null as number | null, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]],
    monetaryAmount: [null as number | null, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
    description: [{ value: '', disabled: true }, [Validators.required, Validators.maxLength(500)]],
  });

  private readonly type = toSignal(this.form.controls.type.valueChanges, { initialValue: this.form.controls.type.value });
  readonly isMonetary = computed(() => this.type() === 'MONETARY');

  constructor() {
    // Montant pour une sanction monétaire, description pour une sanction en nature.
    this.form.controls.type.valueChanges.subscribe((type) => {
      const { monetaryAmount, description } = this.form.controls;
      if (type === 'MONETARY') {
        monetaryAmount.enable();
        description.reset('');
        description.disable();
      } else {
        description.enable();
        monetaryAmount.reset(null);
        monetaryAmount.disable();
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const monetary = v.type === 'MONETARY';
    this.saving.set(true);
    this.errorMessage.set(null);
    this.sanctionService
      .createRule({
        contributionDefinitionId: this.data.contribution.id,
        type: v.type!,
        lateDaysThreshold: Number(v.lateDaysThreshold),
        monetaryAmount: monetary ? Number(v.monetaryAmount) : null,
        description: monetary ? null : v.description!.trim(),
      })
      .subscribe({
        next: (rule) => this.dialogRef.close(rule),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400
              ? 'Règle refusée : la cotisation doit être obligatoire, avec un montant positif (monétaire) ou une description (en nature).'
              : error.status === 403
                ? 'Action réservée à l’administrateur et au trésorier.'
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
