import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import {
  AMOUNT_MODE_LABELS,
  AmountMode,
  ContributionDefinition,
  ContributionFrequency,
  FREQUENCY_LABELS,
  FUND_DESTINATION_LABELS,
  FundDestination,
  VISIBILITY_LABELS,
  Visibility,
} from '../contribution.model';
import { ContributionService } from '../contribution.service';

export interface ContributionEditDialogData {
  definition: ContributionDefinition;
}

/**
 * Modification d'une cotisation existante. Quand des paiements existent
 * (`hasPayments`), montant, mode de montant, caractère obligatoire et
 * destination des fonds sont verrouillés (docs/decisions.md n° 36) ; le
 * backend applique la même règle.
 */
@Component({
  selector: 'app-contribution-edit-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './contribution-edit-dialog.html',
  styleUrl: './contribution-edit-dialog.scss',
})
export class ContributionEditDialog implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly contributionService = inject(ContributionService);
  private readonly dialogRef =
    inject<MatDialogRef<ContributionEditDialog, ContributionDefinition>>(MatDialogRef);
  readonly data = inject<ContributionEditDialogData>(MAT_DIALOG_DATA);

  readonly locked = this.data.definition.hasPayments;
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly amountModes = Object.entries(AMOUNT_MODE_LABELS) as [AmountMode, string][];
  readonly frequencies = Object.entries(FREQUENCY_LABELS) as [ContributionFrequency, string][];
  readonly visibilities = Object.entries(VISIBILITY_LABELS) as [Visibility, string][];
  readonly destinations = Object.entries(FUND_DESTINATION_LABELS) as [FundDestination, string][];

  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    description: [''],
    amountMode: ['FIXED' as AmountMode, Validators.required],
    amount: [null as number | null],
    frequency: ['MONTHLY' as ContributionFrequency, Validators.required],
    mandatory: [true, Validators.required],
    visibility: ['PUBLIC' as Visibility, Validators.required],
    fundDestination: ['TONTINE_FUND' as FundDestination, Validators.required],
  });

  ngOnInit(): void {
    const d = this.data.definition;
    this.form.patchValue({
      name: d.name,
      description: d.description ?? '',
      amountMode: d.amountMode,
      amount: d.amount,
      frequency: d.frequency,
      mandatory: d.mandatory,
      visibility: d.visibility,
      fundDestination: d.fundDestination,
    });
    if (this.locked) {
      for (const name of ['amountMode', 'amount', 'mandatory', 'fundDestination'] as const) {
        this.form.controls[name].disable();
      }
    }
  }

  get isFixed(): boolean {
    return this.form.controls.amountMode.value === 'FIXED';
  }

  submit(): void {
    const amount = this.form.controls.amount.value;
    if (this.isFixed && !this.locked && (amount === null || amount <= 0)) {
      this.form.controls.amount.setErrors({ positive: true });
      this.form.controls.amount.markAsTouched();
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // getRawValue inclut les champs verrouillés (désactivés), renvoyés inchangés.
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);

    this.contributionService
      .update(this.data.definition.id, {
        name: v.name!.trim(),
        description: v.description?.trim() || null,
        amountMode: v.amountMode!,
        amount: v.amountMode === 'FIXED' ? v.amount : null,
        frequency: v.frequency!,
        mandatory: v.mandatory!,
        visibility: v.visibility!,
        fundDestination: v.fundDestination!,
      })
      .subscribe({
        next: (updated) => this.dialogRef.close(updated),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400 || error.status === 409
              ? error.error?.message || 'Les informations saisies sont invalides.'
              : error.status === 403
                ? "Vous n'avez pas les droits pour modifier une cotisation."
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
