import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { formatFcfa } from '../../../shared/utils/format';
import { ContributionTransaction, PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { PaymentService } from '../payment.service';

type ViewerState = 'loading' | 'image' | 'pdf' | 'other' | 'none' | 'error';

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
  private readonly sanitizer = inject(DomSanitizer);
  readonly transaction = inject<ContributionTransaction>(MAT_DIALOG_DATA);

  readonly formatFcfa = formatFcfa;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly state = signal<ViewerState>('loading');
  readonly objectUrl = signal<string | null>(null);
  readonly safeUrl = signal<SafeResourceUrl | null>(null);
  readonly filename = signal('justificatif');

  ngOnInit(): void {
    this.paymentService.downloadProof(this.transaction.id).subscribe({
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
    return `justificatif-${this.transaction.transactionReference}.${ext}`;
  }
}
