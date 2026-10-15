import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { formatFcfa, toIsoDate } from '../../../shared/utils/format';
import {
  ACCOUNT_STYLES,
  Account,
  AccountOperation,
  AccountOperationType,
  AccountSummary,
  OPERABLE_ACCOUNTS,
} from '../fund.model';
import { FundOverviewApi } from '../fund.service';

export interface OperationDialogData {
  type: AccountOperationType;
  accounts: AccountSummary[];
  /** Compte présélectionné (bouton d'une carte). */
  source?: Account;
}

/**
 * Dépense ou virement (decisions.md n° 50) : uniquement depuis et vers les comptes
 * Sanctions, Fond de caisse et Ration, sans dépasser le solde. Le serveur refait
 * tous les contrôles. Se ferme avec l'opération enregistrée, ou undefined.
 */
@Component({
  selector: 'app-operation-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './operation-dialog.html',
  styleUrl: './operation-dialog.scss',
})
export class OperationDialog {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FundOverviewApi);
  private readonly dialogRef = inject<MatDialogRef<OperationDialog, AccountOperation>>(MatDialogRef);
  readonly data = inject<OperationDialogData>(MAT_DIALOG_DATA);

  readonly formatFcfa = formatFcfa;
  readonly accountStyles = ACCOUNT_STYLES;
  readonly isTransfer = this.data.type === 'TRANSFER';
  readonly operable = OPERABLE_ACCOUNTS;
  readonly today = new Date();

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    sourceAccount: [this.data.source ?? (null as Account | null), Validators.required],
    targetAccount: [null as Account | null, this.isTransfer ? Validators.required : []],
    amount: [null as number | null, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
    operationDate: [new Date() as Date | null, Validators.required],
    label: ['', [Validators.required, Validators.maxLength(200)]],
    observation: ['', Validators.maxLength(500)],
  });

  private readonly source = toSignal(this.form.controls.sourceAccount.valueChanges, {
    initialValue: this.form.controls.sourceAccount.value,
  });

  /** Solde disponible du compte débité. */
  readonly available = computed(() => {
    const source = this.source();
    return source ? (this.balanceOf(source) ?? 0) : null;
  });

  readonly targets = computed(() => this.operable.filter((a) => a !== this.source()));

  constructor() {
    this.form.controls.sourceAccount.valueChanges.subscribe((source) => {
      if (this.form.controls.targetAccount.value === source) {
        this.form.controls.targetAccount.setValue(null);
      }
    });
  }

  balanceOf(account: Account): number | undefined {
    return this.data.accounts.find((a) => a.account === account)?.balance;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const available = this.available() ?? 0;
    if (Number(v.amount) > available) {
      this.errorMessage.set(`Solde insuffisant : ${formatFcfa(available)} disponibles sur ce compte.`);
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);
    this.api
      .recordOperation({
        type: this.data.type,
        sourceAccount: v.sourceAccount!,
        targetAccount: this.isTransfer ? v.targetAccount : null,
        amount: Number(v.amount),
        operationDate: toIsoDate(v.operationDate!),
        label: v.label!.trim(),
        observation: v.observation?.trim() || null,
      })
      .subscribe({
        next: (operation) => this.dialogRef.close(operation),
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.errorMessage.set(
            [400, 403, 409].includes(error.status)
              ? error.error?.message || 'Les informations saisies sont invalides.'
              : 'Une erreur est survenue. Vérifiez que le serveur est démarré.',
          );
        },
      });
  }
}
