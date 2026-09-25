import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
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
import { PROOF_ACCEPT, PaymentService, checkProofFile } from '../payment.service';

export interface ProofUploadData {
  transaction: ContributionTransaction;
  /** Ouvert juste après la déclaration : affiche la confirmation de déclaration. */
  justDeclared?: boolean;
}

/**
 * Ajout (ou remplacement) du justificatif d'un paiement encore en attente.
 * Se ferme avec le PaymentProof enregistré, ou undefined si l'utilisateur passe.
 */
@Component({
  selector: 'app-proof-upload-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './proof-upload-dialog.html',
  styleUrl: './proof-upload-dialog.scss',
})
export class ProofUploadDialog {
  private readonly paymentService = inject(PaymentService);
  private readonly dialogRef = inject<MatDialogRef<ProofUploadDialog, PaymentProof>>(MatDialogRef);
  readonly data = inject<ProofUploadData>(MAT_DIALOG_DATA);

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
    this.paymentService.uploadProof(this.data.transaction.id, file).subscribe({
      next: (proof) => this.dialogRef.close(proof),
      error: (error: HttpErrorResponse) => {
        this.uploading.set(false);
        this.errorMessage.set(
          error.status === 400
            ? 'Justificatif refusé : format non accepté, fichier trop volumineux, ou paiement déjà traité.'
            : error.status === 413
              ? 'Fichier trop volumineux (10 Mo maximum).'
              : error.status === 403
                ? 'Vous ne pouvez joindre un justificatif qu\'à vos propres paiements.'
                : "L'envoi a échoué. Vérifiez que le serveur est démarré.",
        );
      },
    });
  }
}
