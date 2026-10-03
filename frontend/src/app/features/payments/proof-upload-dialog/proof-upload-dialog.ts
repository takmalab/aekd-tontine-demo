import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa } from '../../../shared/utils/format';
import {
  ContributionTransaction,
  PAYMENT_OPERATOR_LABELS,
  PaymentProof,
} from '../../contributions/contribution.model';
import { LoanRepayment } from '../../loans/loan.model';
import { LoanService } from '../../loans/loan.service';
import { SavingsEntry } from '../../savings/savings.model';
import { SavingsService } from '../../savings/savings.service';
import { PROOF_ACCEPT, PaymentService, checkProofFile } from '../payment.service';

/** Un paiement de cotisation, un remboursement de prêt OU un versement d'épargne (un seul). */
export interface ProofUploadData {
  transaction?: ContributionTransaction;
  repayment?: LoanRepayment;
  deposit?: SavingsEntry;
  /** Ouvert juste après la déclaration : affiche la confirmation de déclaration. */
  justDeclared?: boolean;
}

/**
 * Ajout (ou remplacement) du justificatif d'un paiement ou d'un remboursement
 * encore en attente. Se ferme avec la réponse du serveur, ou undefined si
 * l'utilisateur passe.
 */
@Component({
  selector: 'app-proof-upload-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './proof-upload-dialog.html',
  styleUrl: './proof-upload-dialog.scss',
})
export class ProofUploadDialog {
  private readonly paymentService = inject(PaymentService);
  private readonly loanService = inject(LoanService);
  private readonly savingsService = inject(SavingsService);
  private readonly dialogRef =
    inject<MatDialogRef<ProofUploadDialog, PaymentProof | LoanRepayment | SavingsEntry>>(MatDialogRef);
  readonly data = inject<ProofUploadData>(MAT_DIALOG_DATA);

  /** « paiement », « remboursement » ou « versement », pour les libellés. */
  readonly noun = this.data.deposit ? 'versement' : this.data.repayment ? 'remboursement' : 'paiement';
  readonly subject = this.data.deposit
    ? { label: "Versement d'épargne", amount: this.data.deposit.amount, operator: this.data.deposit.operator }
    : this.data.repayment
      ? { label: 'Remboursement de prêt', amount: this.data.repayment.amount, operator: this.data.repayment.operator }
      : {
          label: this.data.transaction!.contributionDefinitionName,
          amount: this.data.transaction!.amount,
          operator: this.data.transaction!.operator,
        };

  readonly accept = PROOF_ACCEPT;
  readonly formatFcfa = formatFcfa;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly file = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly dragOver = signal(false);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.pick(input.files?.[0] ?? null);
    input.value = '';
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    this.pick(event.dataTransfer?.files?.[0] ?? null);
  }

  private pick(file: File | null): void {
    this.errorMessage.set(null);
    if (!file) {
      return;
    }
    const problem = checkProofFile(file);
    if (problem) {
      this.file.set(null);
      this.errorMessage.set(problem);
      return;
    }
    this.file.set(file);
  }

  sizeLabel(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} Ko` : `${(bytes / 1024 / 1024).toFixed(1)} Mo`;
  }

  upload(): void {
    const file = this.file();
    if (!file) {
      return;
    }
    this.uploading.set(true);
    this.errorMessage.set(null);
    const upload$: Observable<PaymentProof | LoanRepayment | SavingsEntry> = this.data.deposit
      ? this.savingsService.uploadProof(this.data.deposit.id, file)
      : this.data.repayment
        ? this.loanService.uploadRepaymentProof(this.data.repayment.id, file)
        : this.paymentService.uploadProof(this.data.transaction!.id, file);
    upload$.subscribe({
      next: (proof) => this.dialogRef.close(proof),
      error: (error: HttpErrorResponse) => {
        this.uploading.set(false);
        this.errorMessage.set(
          error.status === 400
            ? `Justificatif refusé : format non accepté, fichier trop volumineux, ou ${this.noun} déjà traité.`
            : error.status === 413
              ? 'Fichier trop volumineux (10 Mo maximum).'
              : error.status === 403
                ? `Vous ne pouvez joindre un justificatif qu'à vos propres ${this.noun}s.`
                : "L'envoi a échoué. Vérifiez que le serveur est démarré.",
        );
      },
    });
  }
}
