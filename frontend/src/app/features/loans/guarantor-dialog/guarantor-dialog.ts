import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { catchError, of } from 'rxjs';
import { GuarantorCandidate, Loan } from '../loan.model';
import { LoanService } from '../loan.service';

/**
 * Le demandeur désigne un autre avaliste (decisions.md n° 52), par exemple après
 * un refus. Se ferme avec l'identifiant du membre choisi, ou undefined.
 */
@Component({
  selector: 'app-guarantor-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <div class="head">
      <span class="head__icon"><mat-icon>handshake</mat-icon></span>
      <div class="head__text">
        <h2 mat-dialog-title class="head__title">Choisir un avaliste</h2>
        <p class="head__subtitle">Le membre choisi devra accepter d'être garant.</p>
      </div>
    </div>
    <mat-dialog-content class="body">
      <mat-form-field appearance="outline" class="full">
        <mat-label>Avaliste</mat-label>
        <mat-select [formControl]="guarantor">
          @for (c of candidates(); track c.memberId) {
            @if (c.memberId !== loan.guarantorMemberId) {
              <mat-option [value]="c.memberId">{{ c.fullName }}</mat-option>
            }
          }
        </mat-select>
        @if (guarantor.hasError('required')) {
          <mat-error>Choisissez un membre</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end" class="actions">
      <button mat-button type="button" mat-dialog-close>Annuler</button>
      <button mat-flat-button type="button" (click)="confirm()">Désigner</button>
    </mat-dialog-actions>
  `,
  styleUrl: '../loan-request-dialog/loan-request-dialog.scss',
})
export class GuarantorDialog {
  private readonly dialogRef = inject<MatDialogRef<GuarantorDialog, string>>(MatDialogRef);
  readonly loan = inject<Loan>(MAT_DIALOG_DATA);

  readonly guarantor = new FormControl<string | null>(null, Validators.required);
  readonly candidates = toSignal(
    inject(LoanService)
      .guarantorCandidates()
      .pipe(catchError(() => of([] as GuarantorCandidate[]))),
    { initialValue: [] as GuarantorCandidate[] },
  );

  confirm(): void {
    if (this.guarantor.invalid) {
      this.guarantor.markAsTouched();
      return;
    }
    this.dialogRef.close(this.guarantor.value!);
  }
}
