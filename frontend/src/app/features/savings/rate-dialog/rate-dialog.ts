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
import { toIsoDate } from '../../../shared/utils/format';
import { SavingsRate, formatMonthlyRate } from '../savings.model';
import { SavingsService } from '../savings.service';

export interface RateDialogData {
  currentRate: number | null;
}

/**
 * Nouveau taux d'intérêt MENSUEL de l'épargne (docs/decisions.md n° 39). Il
 * s'applique aux mois d'épargne se terminant à partir de sa date d'effet ;
 * les mois précédents gardent leur taux.
 */
@Component({
  selector: 'app-rate-dialog',
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
  template: `
    <div class="head">
      <span class="head__icon"><mat-icon>percent</mat-icon></span>
      <div class="head__text">
        <h2 mat-dialog-title class="head__title">Taux d'intérêt de l'épargne</h2>
        <p class="head__subtitle">Taux actuel : {{ formatMonthlyRate(data.currentRate) }}</p>
      </div>
    </div>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="body">
        <p class="hint">
          <mat-icon>info</mat-icon>
          <span>
            Intérêts simples : chaque versement validé rapporte ce taux pour chaque mois complet écoulé. Le nouveau taux
            s'applique aux mois qui se terminent à partir de la date d'effet ; les mois précédents gardent leur taux.
          </span>
        </p>
        <div class="row">
          <mat-form-field appearance="outline">
            <mat-label>Taux mensuel</mat-label>
            <input matInput type="number" inputmode="decimal" min="0" max="100" step="0.001" formControlName="monthlyRate" cdkFocusInitial />
            <span matTextSuffix>% / mois</span>
            @if (form.controls.monthlyRate.invalid) {
              <mat-error>Taux entre 0 et 100</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Date d'effet</mat-label>
            <input matInput [matDatepicker]="picker" formControlName="effectiveFrom" (click)="picker.open()" />
            <mat-datepicker-toggle matIconSuffix [for]="picker" />
            <mat-datepicker #picker />
            @if (form.controls.effectiveFrom.invalid) {
              <mat-error>Date requise (JJ/MM/AAAA)</mat-error>
            }
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Note (facultatif)</mat-label>
          <input matInput formControlName="note" placeholder="Ex. : décision de l'assemblée du 3 octobre" />
        </mat-form-field>
        @if (errorMessage(); as message) {
          <p class="notice" role="alert"><mat-icon>error_outline</mat-icon><span>{{ message }}</span></p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end" class="actions">
        <button mat-button type="button" mat-dialog-close [disabled]="saving()">Annuler</button>
        <button mat-flat-button type="submit" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="20" />
          } @else {
            Enregistrer le taux
          }
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    @use '../../../shared/styles/dialog' as dlg;
    @include dlg.dialog-base;

    .row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .hint {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      margin: 0 0 14px;
      padding: 10px 12px;
      border-radius: 10px;
      background: var(--aekd-blue-soft);
      font-size: 13px;
      line-height: 1.45;

      mat-icon {
        flex: none;
        color: var(--aekd-blue);
      }
    }
    @media (max-width: 599.98px) {
      .row {
        grid-template-columns: 1fr;
        gap: 0;
      }
    }
  `,
})
export class RateDialog {
  private readonly fb = inject(FormBuilder);
  private readonly savingsService = inject(SavingsService);
  private readonly dialogRef = inject<MatDialogRef<RateDialog, SavingsRate>>(MatDialogRef);
  readonly data = inject<RateDialogData>(MAT_DIALOG_DATA);
  readonly formatMonthlyRate = formatMonthlyRate;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    monthlyRate: [this.data.currentRate, [Validators.required, Validators.min(0), Validators.max(100)]],
    effectiveFrom: [new Date() as Date | null, Validators.required],
    note: ['', Validators.maxLength(255)],
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
      .setRate({
        monthlyRate: Number(v.monthlyRate),
        effectiveFrom: toIsoDate(v.effectiveFrom!),
        note: v.note?.trim() || null,
      })
      .subscribe({
        next: (rate) => this.dialogRef.close(rate),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 409 || error.status === 400
              ? error.error?.message || 'Taux refusé.'
              : error.status === 403
                ? 'Action réservée à l’administrateur et au trésorier.'
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
