import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
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
  GuarantorCandidate,
  SavingsDeductionRequest,
} from './loan.model';

/**
 * Prêts (CLAUDE.md §20-§24). Rôles côté backend :
 * - politiques : lecture ouverte, création/désactivation ADMIN uniquement ;
 * - demande : tout membre ; liste complète : ADMIN et TRESORIER ;
 * - approbation, rejet, saisie d'un remboursement reçu : TRESORIER et ADMIN (décision n° 40) ;
 * - le membre déclare le remboursement de son prêt, validé ensuite par le TRESORIER.
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

  /** Refusé par le backend (409) si la politique a déjà été utilisée par un prêt. */
  updatePolicy(id: string, request: LoanPolicyRequest): Observable<LoanPolicy> {
    return this.http.put<LoanPolicy>(`${this.api}/loan-policies/${id}`, request);
  }

  activatePolicy(id: string): Observable<LoanPolicy> {
    return this.http.put<LoanPolicy>(`${this.api}/loan-policies/${id}/activate`, {});
  }

  deactivatePolicy(id: string): Observable<LoanPolicy> {
    return this.http.put<LoanPolicy>(`${this.api}/loan-policies/${id}/deactivate`, {});
  }

  /** Refusé par le backend (409) si la politique a déjà été utilisée par un prêt. */
  deletePolicy(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/loan-policies/${id}`);
  }

  // ---------- Prêts ----------
  /** Jamais bloquée par les règles : la réponse contient l'évaluation de chaque règle (§21). */
  request(request: LoanRequest): Observable<Loan> {
    return this.http.post<Loan>(`${this.api}/loans`, request);
  }

  mine(): Observable<Loan[]> {
    return this.http.get<Loan[]>(`${this.api}/loans/mine`);
  }

  // ---------- Avaliste (decisions.md n° 52) ----------

  guarantorCandidates(): Observable<GuarantorCandidate[]> {
    return this.http.get<GuarantorCandidate[]>(`${this.api}/loans/guarantor-candidates`);
  }

  /** Prêts dont le membre connecté est l'avaliste. */
  guaranteed(): Observable<Loan[]> {
    return this.http.get<Loan[]>(`${this.api}/loans/guaranteed`);
  }

  answerGuarantee(loanId: string, accept: boolean): Observable<Loan> {
    return this.http.put<Loan>(`${this.api}/loans/${loanId}/guarantee/${accept ? 'accept' : 'decline'}`, {});
  }

  /** Prélèvement sur l'épargne de l'emprunteur ou de l'avaliste (decisions.md n° 53). */
  deductFromSavings(loanId: string, request: SavingsDeductionRequest): Observable<LoanRepayment> {
    return this.http.post<LoanRepayment>(`${this.api}/loans/${loanId}/savings-deduction`, request);
  }

  changeGuarantor(loanId: string, guarantorMemberId: string): Observable<Loan> {
    return this.http.put<Loan>(`${this.api}/loans/${loanId}/guarantor`, { guarantorMemberId });
  }

  list(status?: LoanStatus): Observable<Loan[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<Loan[]>(`${this.api}/loans`, { params });
  }

  get(id: string): Observable<Loan> {
    return this.http.get<Loan>(`${this.api}/loans/${id}`);
  }

  /** Argent d'épargne disponible pour prêter : plafond de l'approbation (décision n° 46). */
  availableForLending(): Observable<{ balance: number }> {
    return this.http.get<{ balance: number }>(`${this.api}/funds/available-for-lending`);
  }

  /** Refusé (409) au-delà de l'argent disponible, (403) sur son propre prêt. */
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

  /** Trésorier : remboursement reçu, comptabilisé immédiatement. */
  recordRepayment(loanId: string, request: LoanRepaymentRequest): Observable<LoanRepayment> {
    return this.http.post<LoanRepayment>(`${this.api}/loans/${loanId}/repayments`, request);
  }

  /** Membre : déclare un remboursement de son prêt (en attente de validation). */
  declareRepayment(loanId: string, request: LoanRepaymentRequest): Observable<LoanRepayment> {
    return this.http.post<LoanRepayment>(`${this.api}/loans/${loanId}/repayments/declare`, request);
  }

  pendingRepayments(): Observable<LoanRepayment[]> {
    return this.http.get<LoanRepayment[]>(`${this.api}/loans/repayments/pending`);
  }

  validateRepayment(id: string): Observable<LoanRepayment> {
    return this.http.put<LoanRepayment>(`${this.api}/loans/repayments/${id}/validate`, {});
  }

  rejectRepayment(id: string): Observable<LoanRepayment> {
    return this.http.put<LoanRepayment>(`${this.api}/loans/repayments/${id}/reject`, {});
  }

  uploadRepaymentProof(id: string, file: File): Observable<LoanRepayment> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<LoanRepayment>(`${this.api}/loans/repayments/${id}/proof`, body);
  }

  downloadRepaymentProof(id: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.api}/loans/repayments/${id}/proof`, { observe: 'response', responseType: 'blob' });
  }
}
