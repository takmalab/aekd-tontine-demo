import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppliedSanction, SanctionCandidate, SanctionRule, SanctionRuleRequest } from './sanction.model';

/**
 * Sanctions (CLAUDE.md §18-§19). Rôles côté backend :
 * - règles : lecture et création ADMIN + TRESORIER (uniquement sur une cotisation obligatoire) ;
 * - candidats, application, annulation : TRESORIER uniquement ;
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

  applied(contributionDefinitionId: string): Observable<AppliedSanction[]> {
    return this.http.get<AppliedSanction[]>(`${this.baseUrl}/applied`, {
      params: new HttpParams().set('contributionDefinitionId', contributionDefinitionId),
    });
  }
}
