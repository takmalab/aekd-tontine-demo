import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { toIsoDate } from '../../shared/utils/format';
import { ContributionService } from '../contributions/contribution.service';
import { Session, SessionContribution, SessionRequest, SessionTiming } from './session.model';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly contributionService = inject(ContributionService);
  private readonly baseUrl = `${environment.apiUrl}/sessions`;

  /** Séances triées de la plus récente à la plus ancienne (tri fait par le backend). */
  list(): Observable<Session[]> {
    return this.http.get<Session[]>(this.baseUrl);
  }

  get(id: string): Observable<Session> {
    return this.http.get<Session>(`${this.baseUrl}/${id}`);
  }

  create(request: SessionRequest): Observable<Session> {
    return this.http.post<Session>(this.baseUrl, request);
  }

  /**
   * Cotisations rattachées à une séance via leurs périodes (CLAUDE.md §7, §13).
   * Le backend n'expose pas encore d'endpoint "cotisations d'une séance" : on lit
   * les cotisations visibles puis leurs périodes, et on garde celles de la séance.
   * Chaque cotisation reste une entrée distincte (§9 : pas de fusion).
   */
  contributionsOf(sessionId: string): Observable<SessionContribution[]> {
    return this.contributionService.list().pipe(
      switchMap((definitions) => {
        if (definitions.length === 0) {
          return of([] as SessionContribution[]);
        }
        return forkJoin(
          definitions.map((definition) =>
            this.contributionService.listPeriods(definition.id).pipe(
              map((periods) =>
                periods
                  .filter((period) => period.sessionId === sessionId)
                  .map((period) => ({ definition, period })),
              ),
            ),
          ),
        ).pipe(map((groups) => groups.flat()));
      }),
    );
  }

  /** Aujourd'hui = jour de la séance ; avant = à venir ; après = passée. */
  timingOf(session: Session, today: Date = new Date()): SessionTiming {
    const now = toIsoDate(today);
    if (now < session.date) {
      return 'UPCOMING';
    }
    if (now > session.date) {
      return 'PAST';
    }
    return 'CURRENT';
  }
}
