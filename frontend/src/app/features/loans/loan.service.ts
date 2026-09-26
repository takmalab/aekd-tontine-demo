import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Loan,
  LoanApprovalRequest,
  LoanBalance,
  LoanPolicy,
  LoanPolicyRequest,
  LoanRepayment,
  LoanRepaymentRequest,
  LoanRequest,
  LoanStatus,
} from './loan.model';

/**
 * Prêts (CLAUDE.md §20-§24). Rôles côté backend :
 * - politiques : lecture ouverte, création/désactivation ADMIN uniquement ;
 * - demande : tout membre ; liste complète : ADMIN et TRESORIER ;
 * - approbation, rejet, remboursement : TRESORIER uniquement.
 */
@Injectable({ providedIn: 'root' })
export class LoanService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  // ---------- Politiques ----------
  listPolicies(): Observable<LoanPolicy[]> {
    return this.http.get<LoanPolicy[]>(`${this.api}/loan-policies`);
  }

  createPolicy(request: LoanPolicyRequest): Observable<LoanPolicy> {
    return this.http.post<LoanPolicy>(`${this.api}/loan-policies`, request);
  }

  deactivatePolicy(id: string): Observable<LoanPolicy> {
    return this.http.put<LoanPolicy>(`${this.api}/loan-policies/${id}/deactivate`, {});
  }

  // ---------- Prêts ----------
  /** Jamais bloquée par les règles : la réponse contient l'évaluation de chaque règle (§21). */
  request(request: LoanRequest): Observable<Loan> {
    return this.http.post<Loan>(`${this.api}/loans`, request);
  }

  mine(): Observable<Loan[]> {
    return this.http.get<Loan[]>(`${this.api}/loans/mine`);
  }

  list(status?: LoanStatus): Observable<Loan[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<Loan[]>(`${this.api}/loans`, { params });
  }

  get(id: string): Observable<Loan> {
    return this.http.get<Loan>(`${this.api}/loans/${id}`);
  }

  approve(id: string, request: LoanApprovalRequest): Observable<Loan> {
    return this.http.put<Loan>(`${this.api}/loans/${id}/approve`, request);
  }

  reject(id: string, rejectionReason: string): Observable<Loan> {
    return this.http.put<Loan>(`${this.api}/loans/${id}/reject`, { rejectionReason });
  }

  // ---------- Remboursements ----------
  repayments(loanId: string): Observable<LoanRepayment[]> {
    return this.http.get<LoanRepayment[]>(`${this.api}/loans/${loanId}/repayments`);
  }

  balance(loanId: string): Observable<LoanBalance> {
    return this.http.get<LoanBalance>(`${this.api}/loans/${loanId}/balance`);
  }

  recordRepayment(loanId: string, request: LoanRepaymentRequest): Observable<LoanRepayment> {
    return this.http.post<LoanRepayment>(`${this.api}/loans/${loanId}/repayments`, request);
  }
}
