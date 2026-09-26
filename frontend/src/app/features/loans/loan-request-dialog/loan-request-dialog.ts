import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa } from '../../../shared/utils/format';
import { Loan, LoanPolicy } from '../loan.model';
import { LoanService } from '../loan.service';
import { RuleChecklist } from '../rule-checklist/rule-checklist';

export interface LoanRequestData {
  /**
   * Politiques actives. Le backend applique la plus récente ; la liste ne donnant pas
   * de date de création, les limites ne sont affichées que s'il y en a exactement une.
   */
  activePolicies: LoanPolicy[];
}

/**
 * Demande de prêt (CLAUDE.md §21). La demande n'est jamais bloquée par les règles :
 * elle est enregistrée puis la réponse montre, règle par règle, ce qui est respecté
 * ou non. Les limites de la politique sont affichées à titre indicatif.
 */
@Component({
  selector: 'app-loan-request-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RuleChecklist,
  ],
  templateUrl: './loan-request-dialog.html',
  styleUrl: './loan-request-dialog.scss',
})
export class LoanRequestDialog {
  private readonly fb = inject(FormBuilder);
  private readonly loanService = inject(LoanService);
  private readonly dialogRef = inject<MatDialogRef<LoanRequestDialog, Loan>>(MatDialogRef);
  readonly data = inject<LoanRequestData>(MAT_DIALOG_DATA);
  readonly formatFcfa = formatFcfa;
  readonly policy: LoanPolicy | null =
    this.data.activePolicies.length === 1 ? this.data.activePolicies[0] : null;

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  /** Prêt enregistré : affiche le résultat de l'évaluation des règles. */
  readonly result = signal<Loan | null>(null);

  readonly form = this.fb.group({
    amount: [null as number | null, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
    durationMonths: [null as number | null, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.loanService
      .request({ amount: Number(v.amount), durationMonths: Number(v.durationMonths), reason: v.reason!.trim() })
      .subscribe({
        next: (loan) => {
          this.saving.set(false);
          this.result.set(loan);
        },
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            error.status === 400
              ? "Demande impossible : aucune politique de prêt n'est active, ou les informations sont invalides."
              : error.status === 403
                ? "Ce compte n'est rattaché à aucun membre : il ne peut pas demander de prêt."
                : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }

  hasFailedRule(loan: Loan): boolean {
    return loan.ruleEvaluations.some((r) => !r.respected);
  }

  close(): void {
    this.dialogRef.close(this.result() ?? undefined);
  }
}
