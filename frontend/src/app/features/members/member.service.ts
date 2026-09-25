import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Member } from './member.model';

/**
 * Lecture seule des membres, pour les sélecteurs (récepteur, bénéficiaires…).
 * L'écran de gestion des membres fera l'objet d'une fonctionnalité séparée.
 * `/api/members` est réservé à ADMIN et TRESORIER.
 */
@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);

  list(): Observable<Member[]> {
    return this.http.get<Member[]>(`${environment.apiUrl}/members`);
  }
}
