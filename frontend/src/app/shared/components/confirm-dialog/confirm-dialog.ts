import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  icon?: string;
  /**
   * Libellé d'un motif obligatoire à saisir (ex. rejet d'un paiement, décision n° 66).
   * Le dialogue se ferme alors avec le motif (texte) au lieu de `true`.
   */
  reasonLabel?: string;
}

/**
 * Confirmation avant une action sensible (CLAUDE.md §30).
 * Se ferme avec `true` si l'utilisateur confirme (ou avec le motif saisi si `reasonLabel`
 * est fourni), `undefined` sinon.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <div class="confirm">
      <span class="confirm__icon"><mat-icon>{{ data.icon ?? 'help' }}</mat-icon></span>
      <h2 mat-dialog-title class="confirm__title">{{ data.title }}</h2>
      <mat-dialog-content class="confirm__message">
        {{ data.message }}
        @if (data.reasonLabel) {
          <mat-form-field appearance="outline" class="confirm__reason">
            <mat-label>{{ data.reasonLabel }}</mat-label>
            <textarea matInput rows="3" [formControl]="reason" cdkFocusInitial></textarea>
            @if (reason.hasError('required') && reason.touched) {
              <mat-error>Le motif est obligatoire</mat-error>
            }
            @if (reason.hasError('maxlength')) {
              <mat-error>500 caractères maximum</mat-error>
            }
          </mat-form-field>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end" class="confirm__actions">
        <button mat-button type="button" mat-dialog-close>{{ data.cancelLabel ?? 'Annuler' }}</button>
        @if (data.reasonLabel) {
          <button mat-flat-button type="button" (click)="confirm()">{{ data.confirmLabel ?? 'Confirmer' }}</button>
        } @else {
          <button mat-flat-button type="button" (click)="confirm()" cdkFocusInitial>
            {{ data.confirmLabel ?? 'Confirmer' }}
          </button>
        }
      </mat-dialog-actions>
    </div>
  `,
  styles: `
    .confirm {
      padding: 22px 8px 8px;
    }
    .confirm__icon {
      display: grid;
      place-items: center;
      width: 48px;
      height: 48px;
      margin: 0 24px 8px;
      border-radius: 14px;
      background: var(--aekd-orange-soft);
      color: var(--aekd-orange);
    }
    .confirm__title {
      font-size: 19px !important;
      font-weight: 700 !important;
      padding-top: 4px !important;
      color: var(--aekd-gray);
    }
    .confirm__title::before {
      display: none !important;
    }
    .confirm__message {
      font-size: 14px;
      line-height: 1.5;
      color: var(--aekd-gray-muted) !important;
    }
    .confirm__reason {
      display: block;
      width: 100%;
      margin-top: 14px;
    }
    .confirm__actions {
      gap: 8px;
      padding: 8px 24px 16px !important;
    }
    .confirm__actions button[mat-flat-button] {
      border-radius: 10px;
    }
  `,
})
export class ConfirmDialog {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ConfirmDialog, true | string>>(MatDialogRef);
  readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] });

  confirm(): void {
    if (!this.data.reasonLabel) {
      this.dialogRef.close(true);
      return;
    }
    const text = this.reason.value.trim();
    if (!text || this.reason.invalid) {
      this.reason.setValue(text);
      this.reason.markAsTouched();
      return;
    }
    this.dialogRef.close(text);
  }
}
