import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AppliedSanction,
  SanctionCandidate,
  SanctionPayment,
  SanctionPaymentRequest,
  SanctionRule,
  SanctionRuleRequest,
} from './sanction.model';

/**
 * Sanctions (CLAUDE.md §18-§19). Rôles côté backend :
 * - règles : lecture et création ADMIN + TRESORIER (uniquement sur une cotisation obligatoire) ;
 * - candidats, application, annulation : TRESORIER et ADMIN (décision n° 40) ;
 * - sanctions appliquées : toutes pour ADMIN + TRESORIER, les siennes pour un membre.
 */
@Injectable({ providedIn: 'root' })
export class SanctionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/sanctions`;

  listRules(contributionDefinitionId: string): Observable<SanctionRule[]> {
    return this.http.get<SanctionRule[]>(this.baseUrl, { params: new HttpParams().set('contributionDefinitionId', contributionDefinitionId) });
  }

  createRule(request: SanctionRuleRequest): Observable<SanctionRule> {
    return this.http.post<SanctionRule>(this.baseUrl, request);
  }

  candidates(ruleId: string): Observable<SanctionCandidate[]> {
    return this.http.get<SanctionCandidate[]>(`${this.baseUrl}/${ruleId}/candidates`);
  }

  apply(ruleId: string, memberId: string, contributionPeriodId: string): Observable<AppliedSanction> {
    return this.http.post<AppliedSanction>(`${this.baseUrl}/${ruleId}/apply`, { memberId, contributionPeriodId });
  }

  cancel(appliedId: string): Observable<AppliedSanction> {
    return this.http.put<AppliedSanction>(`${this.baseUrl}/applied/${appliedId}/cancel`, {});
  }

  mine(): Observable<AppliedSanction[]> {
    return this.http.get<AppliedSanction[]>(`${this.baseUrl}/applied/mine`);
  }

  /** Paiement des sanctions (decisions.md n° 50) : déclaré par le membre, validé par le trésorier. */
  declarePayment(appliedId: string, request: SanctionPaymentRequest): Observable<SanctionPayment> {
    return this.http.post<SanctionPayment>(`${this.baseUrl}/applied/${appliedId}/payments`, request);
  }

  uploadPaymentProof(paymentId: string, file: File): Observable<SanctionPayment> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<SanctionPayment>(`${this.baseUrl}/payments/${paymentId}/proof`, body);
  }

  downloadPaymentProof(paymentId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/payments/${paymentId}/proof`, { observe: 'response', responseType: 'blob' });
  }

  myPayments(): Observable<SanctionPayment[]> {
    return this.http.get<SanctionPayment[]>(`${this.baseUrl}/payments/mine`);
  }

  pendingPayments(): Observable<SanctionPayment[]> {
    return this.http.get<SanctionPayment[]>(`${this.baseUrl}/payments/pending`);
  }

  validatePayment(paymentId: string): Observable<SanctionPayment> {
    return this.http.put<SanctionPayment>(`${this.baseUrl}/payments/${paymentId}/validate`, {});
  }

  rejectPayment(paymentId: string): Observable<SanctionPayment> {
    return this.http.put<SanctionPayment>(`${this.baseUrl}/payments/${paymentId}/reject`, {});
  }

  applied(contributionDefinitionId: string): Observable<AppliedSanction[]> {
    return this.http.get<AppliedSanction[]>(`${this.baseUrl}/applied`, {
      params: new HttpParams().set('contributionDefinitionId', contributionDefinitionId),
    });
  }
}
