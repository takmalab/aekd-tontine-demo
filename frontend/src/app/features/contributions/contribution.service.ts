import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ContributionDefinition, ContributionPeriod } from './contribution.model';

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
}
