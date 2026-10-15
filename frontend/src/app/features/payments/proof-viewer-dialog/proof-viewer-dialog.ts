import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa } from '../../../shared/utils/format';
import { ContributionTransaction, PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { LoanRepayment } from '../../loans/loan.model';
import { LoanService } from '../../loans/loan.service';
import { SavingsEntry } from '../../savings/savings.model';
import { SavingsService } from '../../savings/savings.service';
import { SanctionPayment } from '../../sanctions/sanction.model';
import { SanctionService } from '../../sanctions/sanction.service';
import { PaymentService } from '../payment.service';

type ViewerState = 'loading' | 'image' | 'pdf' | 'other' | 'none' | 'error';

/** Un paiement de cotisation, un remboursement de prêt OU un versement d'épargne (un seul). */
export interface ProofViewerData {
  transaction?: ContributionTransaction;
  repayment?: LoanRepayment;
  deposit?: SavingsEntry;
  sanctionPayment?: SanctionPayment;
}

/**
 * Consultation du justificatif d'un paiement (CLAUDE.md §33 étape 6 : le trésorier
 * consulte le justificatif avant de valider). Le fichier est récupéré en Blob
 * (l'API exige le JWT) puis affiché dans l'application ou téléchargé.
 */
@Component({
  selector: 'app-proof-viewer-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './proof-viewer-dialog.html',
  styleUrl: './proof-viewer-dialog.scss',
})
export class ProofViewerDialog implements OnInit, OnDestroy {
  private readonly paymentService = inject(PaymentService);
  private readonly loanService = inject(LoanService);
  private readonly savingsService = inject(SavingsService);
  private readonly sanctionService = inject(SanctionService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly data = inject<ProofViewerData>(MAT_DIALOG_DATA);

  /** « paiement », « remboursement » ou « versement », pour les libellés. */
  readonly noun = this.data.deposit ? 'versement' : this.data.repayment ? 'remboursement' : 'paiement';
  readonly subject = this.data.sanctionPayment
    ? {
        member: this.data.sanctionPayment.memberFullName,
        label: 'Sanction – ' + this.data.sanctionPayment.sanctionLabel,
        amount: this.data.sanctionPayment.amount,
        operator: this.data.sanctionPayment.operator,
        reference: this.data.sanctionPayment.reference,
      }
    : this.data.deposit
    ? {
        member: this.data.deposit.memberFullName,
        label: "Versement d'épargne",
        amount: this.data.deposit.amount,
        operator: this.data.deposit.operator,
        reference: this.data.deposit.reference,
      }
    : this.data.repayment
    ? {
        member: this.data.repayment.memberFullName,
        label: 'Remboursement de prêt',
        amount: this.data.repayment.amount,
        operator: this.data.repayment.operator,
        reference: this.data.repayment.reference,
      }
    : {
        member: this.data.transaction!.memberFullName,
        label: this.data.transaction!.contributionDefinitionName,
        amount: this.data.transaction!.amount,
        operator: this.data.transaction!.operator,
        reference: this.data.transaction!.transactionReference,
      };

  readonly formatFcfa = formatFcfa;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly state = signal<ViewerState>('loading');
  readonly objectUrl = signal<string | null>(null);
  readonly safeUrl = signal<SafeResourceUrl | null>(null);
  readonly filename = signal('justificatif');

  ngOnInit(): void {
    const download$: Observable<HttpResponse<Blob>> = this.data.sanctionPayment
      ? this.sanctionService.downloadPaymentProof(this.data.sanctionPayment.id)
      : this.data.deposit
      ? this.savingsService.downloadProof(this.data.deposit.id)
      : this.data.repayment
        ? this.loanService.downloadRepaymentProof(this.data.repayment.id)
        : this.paymentService.downloadProof(this.data.transaction!.id);
    download$.subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          this.state.set('none');
          return;
        }
        const url = URL.createObjectURL(blob);
        this.objectUrl.set(url);
        this.safeUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
        this.filename.set(this.resolveFilename(response.headers.get('Content-Disposition'), blob.type));
        this.state.set(
          blob.type.startsWith('image/') ? 'image' : blob.type === 'application/pdf' ? 'pdf' : 'other',
        );
      },
      error: (error: HttpErrorResponse) => this.state.set(error.status === 404 ? 'none' : 'error'),
    });
  }

  ngOnDestroy(): void {
    const url = this.objectUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
  }

  private resolveFilename(disposition: string | null, type: string): string {
    const match = disposition ? /filename="?([^"]+)"?/.exec(disposition) : null;
    if (match) {
      return match[1];
    }
    const ext = type === 'application/pdf' ? 'pdf' : type === 'image/png' ? 'png' : 'jpg';
    return `justificatif-${this.subject.reference}.${ext}`;
  }
}
