import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ContributionTransaction,
  ContributionTransactionRequest,
  PaymentProof,
} from '../contributions/contribution.model';

/** Formats et taille acceptés par le backend (PaymentProofService) — le backend reste l'autorité. */
export const PROOF_ACCEPT = '.jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf';
export const PROOF_MAX_BYTES = 10 * 1024 * 1024;
const PROOF_EXTENSIONS = ['jpg', 'jpeg', 'png', 'pdf'];
const PROOF_TYPES = ['image/jpeg', 'image/png', 'application/pdf'];

/** Pré-contrôle côté client d'un justificatif ; renvoie un message d'erreur ou null. */
export function checkProofFile(file: File): string | null {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  if (!PROOF_EXTENSIONS.includes(extension) || (file.type && !PROOF_TYPES.includes(file.type))) {
    return 'Format non accepté : utilisez une image JPG/PNG ou un PDF.';
  }
  if (file.size > PROOF_MAX_BYTES) {
    return 'Fichier trop volumineux (10 Mo maximum).';
  }
  if (file.size === 0) {
    return 'Le fichier est vide.';
  }
  return null;
}

/**
 * Déclaration, suivi et validation des paiements (CLAUDE.md §14-§16).
 * Un paiement déclaré n'est pas encaissé : seul un paiement validé par le
 * trésorier compte dans les fonds.
 */
@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/contributions`;

  declare(contributionId: string, request: ContributionTransactionRequest): Observable<ContributionTransaction> {
    return this.http.post<ContributionTransaction>(`${this.baseUrl}/${contributionId}/payments`, request);
  }

  /** Paiements déclarés par le membre connecté. */
  mine(): Observable<ContributionTransaction[]> {
    return this.http.get<ContributionTransaction[]>(`${this.baseUrl}/payments/mine`);
  }

  /** Paiements en attente (ADMIN et TRESORIER). */
  pending(): Observable<ContributionTransaction[]> {
    return this.http.get<ContributionTransaction[]>(`${this.baseUrl}/payments/pending`);
  }

  /** TRESORIER uniquement (l'ADMIN ne fait que consulter — CLAUDE.md §6). */
  validate(id: string): Observable<ContributionTransaction> {
    return this.http.put<ContributionTransaction>(`${this.baseUrl}/payments/${id}/validate`, {});
  }

  /** TRESORIER uniquement. */
  reject(id: string): Observable<ContributionTransaction> {
    return this.http.put<ContributionTransaction>(`${this.baseUrl}/payments/${id}/reject`, {});
  }

  /** Ajoute ou remplace le justificatif d'un paiement encore en attente. */
  uploadProof(transactionId: string, file: File): Observable<PaymentProof> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<PaymentProof>(`${this.baseUrl}/payments/${transactionId}/proof`, body);
  }

  /** Télécharge le justificatif (le JWT est ajouté par l'intercepteur, d'où un Blob plutôt qu'un lien direct). */
  downloadProof(transactionId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/payments/${transactionId}/proof`, {
      responseType: 'blob',
      observe: 'response',
    });
  }
}
