import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ContributionDefinition,
  ContributionDefinitionRequest,
  ContributionMemberLink,
  ContributionPeriod,
  ContributionPeriodRequest,
} from './contribution.model';

@Injectable({ providedIn: 'root' })
export class ContributionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/contributions`;

  /** Cotisations visibles par l'utilisateur courant (filtrage de visibilité fait par le backend). */
  list(): Observable<ContributionDefinition[]> {
    return this.http.get<ContributionDefinition[]>(this.baseUrl);
  }

  listPeriods(contributionId: string): Observable<ContributionPeriod[]> {
    return this.http.get<ContributionPeriod[]>(`${this.baseUrl}/${contributionId}/periods`);
  }

  get(id: string): Observable<ContributionDefinition> {
    return this.http.get<ContributionDefinition>(`${this.baseUrl}/${id}`);
  }

  /** Crée la cotisation en brouillon (DRAFT). */
  create(request: ContributionDefinitionRequest): Observable<ContributionDefinition> {
    return this.http.post<ContributionDefinition>(this.baseUrl, request);
  }

  /** DRAFT -> ACTIVE : action explicite, après relecture (CLAUDE.md §31). */
  activate(id: string): Observable<ContributionDefinition> {
    return this.http.put<ContributionDefinition>(`${this.baseUrl}/${id}/activate`, {});
  }

  addParticipant(id: string, memberId: string): Observable<ContributionMemberLink> {
    return this.http.post<ContributionMemberLink>(`${this.baseUrl}/${id}/participants`, { memberId });
  }

  listParticipants(id: string): Observable<ContributionMemberLink[]> {
    return this.http.get<ContributionMemberLink[]>(`${this.baseUrl}/${id}/participants`);
  }

  addBeneficiary(id: string, memberId: string): Observable<ContributionMemberLink> {
    return this.http.post<ContributionMemberLink>(`${this.baseUrl}/${id}/beneficiaries`, { memberId });
  }

  listBeneficiaries(id: string): Observable<ContributionMemberLink[]> {
    return this.http.get<ContributionMemberLink[]>(`${this.baseUrl}/${id}/beneficiaries`);
  }

  addPeriod(id: string, request: ContributionPeriodRequest): Observable<ContributionPeriod> {
    return this.http.post<ContributionPeriod>(`${this.baseUrl}/${id}/periods`, request);
  }
}
