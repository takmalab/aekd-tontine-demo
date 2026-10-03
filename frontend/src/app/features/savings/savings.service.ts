import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MemberSavings,
  SavingsDepositRequest,
  SavingsEntry,
  SavingsOverview,
  SavingsRate,
  SavingsRateRequest,
} from './savings.model';

/**
 * Épargne (docs/decisions.md n° 39). Rôles côté backend :
 * - membre : son épargne, versement libre (« Épargner »), justificatif ;
 * - ADMIN / TRESORIER : états généraux, historique, épargne d'un membre, taux ;
 * - TRESORIER : validation / rejet des versements.
 */
@Injectable({ providedIn: 'root' })
export class SavingsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/savings`;

  mine(): Observable<MemberSavings> {
    return this.http.get<MemberSavings>(`${this.baseUrl}/me/overview`);
  }

  ofMember(memberId: string): Observable<MemberSavings> {
    return this.http.get<MemberSavings>(`${this.baseUrl}/members/${memberId}/overview`);
  }

  declare(request: SavingsDepositRequest): Observable<SavingsEntry> {
    return this.http.post<SavingsEntry>(`${this.baseUrl}/deposits`, request);
  }

  uploadProof(depositId: string, file: File): Observable<SavingsEntry> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<SavingsEntry>(`${this.baseUrl}/deposits/${depositId}/proof`, body);
  }

  downloadProof(depositId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.baseUrl}/deposits/${depositId}/proof`, { observe: 'response', responseType: 'blob' });
  }

  pending(): Observable<SavingsEntry[]> {
    return this.http.get<SavingsEntry[]>(`${this.baseUrl}/deposits/pending`);
  }

  validate(depositId: string): Observable<SavingsEntry> {
    return this.http.put<SavingsEntry>(`${this.baseUrl}/deposits/${depositId}/validate`, {});
  }

  reject(depositId: string): Observable<SavingsEntry> {
    return this.http.put<SavingsEntry>(`${this.baseUrl}/deposits/${depositId}/reject`, {});
  }

  overview(): Observable<SavingsOverview> {
    return this.http.get<SavingsOverview>(`${this.baseUrl}/overview`);
  }

  entries(): Observable<SavingsEntry[]> {
    return this.http.get<SavingsEntry[]>(`${this.baseUrl}/entries`);
  }

  rates(): Observable<SavingsRate[]> {
    return this.http.get<SavingsRate[]>(`${this.baseUrl}/rates`);
  }

  setRate(request: SavingsRateRequest): Observable<SavingsRate> {
    return this.http.post<SavingsRate>(`${this.baseUrl}/rates`, request);
  }
}
