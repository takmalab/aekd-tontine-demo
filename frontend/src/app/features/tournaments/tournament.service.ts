import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Tournament, TournamentRequest, TournamentUpdateRequest } from './tournament.model';

@Injectable({ providedIn: 'root' })
export class TournamentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/tournaments`;

  /** Du plus récent au plus ancien. */
  list(): Observable<Tournament[]> {
    return this.http.get<Tournament[]>(this.baseUrl);
  }

  /** Crée le tournoi et génère tout de suite la première séance si sa date de création est atteinte. */
  create(request: TournamentRequest): Observable<Tournament> {
    return this.http.post<Tournament>(this.baseUrl, request);
  }

  update(id: string, request: TournamentUpdateRequest): Observable<Tournament> {
    return this.http.put<Tournament>(`${this.baseUrl}/${id}`, request);
  }

  /** Arrêt définitif : plus aucune séance générée, les séances existantes restent. */
  stop(id: string): Observable<Tournament> {
    return this.http.put<Tournament>(`${this.baseUrl}/${id}/stop`, {});
  }

  /** Refusé (409) si une séance a déjà été générée. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
