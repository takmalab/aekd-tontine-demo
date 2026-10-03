import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, finalize } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Miroir de ListExportRequest (backend) : la liste telle qu'affichée à l'écran. */
export interface ListExportRequest {
  title: string;
  filters?: string | null;
  columns: string[];
  rows: string[][];
  rightAlign?: number[];
}

/**
 * Téléchargement des documents PDF générés par le backend (reçus, rapport de
 * séance, relevés, exports de listes — docs/decisions.md n° 37). Le jeton est
 * ajouté par l'intercepteur ; les erreurs sont affichées ici.
 */
@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly baseUrl = `${environment.apiUrl}/documents`;

  /** Clé du document en cours de génération (pour désactiver le bouton concerné). */
  readonly busy = signal<string | null>(null);

  paymentReceipt(transactionId: string): void {
    this.get(`payments/${transactionId}/receipt`, 'recu-paiement.pdf');
  }

  repaymentReceipt(repaymentId: string): void {
    this.get(`repayments/${repaymentId}/receipt`, 'recu-remboursement.pdf');
  }

  sessionReport(sessionId: string): void {
    this.get(`sessions/${sessionId}/report`, 'rapport-seance.pdf');
  }

  memberStatement(memberId: string): void {
    this.get(`members/${memberId}/statement`, 'releve.pdf');
  }

  myStatement(): void {
    this.get('members/me/statement', 'mon-releve.pdf');
  }

  exportList(request: ListExportRequest): void {
    this.save(
      'exports',
      this.http.post(`${this.baseUrl}/exports`, request, { observe: 'response', responseType: 'blob' }),
      'export.pdf',
    );
  }

  private get(path: string, fallbackName: string): void {
    this.save(
      path,
      this.http.get(`${this.baseUrl}/${path}`, { observe: 'response', responseType: 'blob' }),
      fallbackName,
    );
  }

  private save(key: string, request: Observable<HttpResponse<Blob>>, fallbackName: string): void {
    this.busy.set(key);
    this.snackBar.open('Génération du PDF…', undefined, { duration: 2500 });
    request.pipe(finalize(() => this.busy.set(null))).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          return;
        }
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filenameOf(response.headers.get('Content-Disposition')) ?? fallbackName;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.snackBar.dismiss();
      },
      error: (err: HttpErrorResponse) => void this.showError(err),
    });
  }

  /** Le corps d'erreur arrive sous forme de Blob (responseType 'blob') : on en lit le message métier. */
  private async showError(err: HttpErrorResponse): Promise<void> {
    let message: string | null = null;
    if (err.error instanceof Blob) {
      try {
        message = JSON.parse(await err.error.text())?.message || null;
      } catch {
        message = null;
      }
    }
    const fallback =
      err.status === 403 || err.status === 404
        ? "Ce document n'est pas accessible."
        : 'La génération du PDF a échoué. Vérifiez que le serveur est démarré.';
    this.snackBar.open(message ?? fallback, 'OK', { duration: 6000 });
  }
}

/** `attachment; filename="releve-jean-mbarga.pdf"` -> `releve-jean-mbarga.pdf`. */
function filenameOf(header: string | null): string | null {
  const match = header?.match(/filename="?([^";]+)"?/i);
  return match ? match[1] : null;
}
