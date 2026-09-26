import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Member, MemberUpdateRequest } from './member.model';

/**
 * Gestion des membres (CLAUDE.md §6). `/api/members` (liste, détail) et la
 * mise à jour sont réservés à ADMIN et TRESORIER côté lecture ; la mise à
 * jour elle-même est réservée à ADMIN côté backend (`MemberController.update`).
 * `/api/members/mine` reste ouvert à tout utilisateur ayant un profil membre.
 */
@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);

  list(): Observable<Member[]> {
    return this.http.get<Member[]>(`${environment.apiUrl}/members`);
  }

  /** Profil membre de l'utilisateur connecté (ouvert à tout utilisateur ayant un profil membre). */
  mine(): Observable<Member> {
    return this.http.get<Member>(`${environment.apiUrl}/members/mine`);
  }

  update(id: string, request: MemberUpdateRequest): Observable<Member> {
    return this.http.put<Member>(`${environment.apiUrl}/members/${id}`, request);
  }
}
