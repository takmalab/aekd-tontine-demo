import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppUser, CreateUserRequest, UpdateUserRequest, UserDeletionImpact } from './user.model';

/**
 * Gestion des comptes utilisateurs (CLAUDE.md §6) : réservée à l'ADMIN
 * (UserController est protégé par `@PreAuthorize("hasRole('ADMIN')")`
 * au niveau de la classe).
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  create(request: CreateUserRequest): Observable<AppUser> {
    return this.http.post<AppUser>(`${environment.apiUrl}/users`, request);
  }

  list(): Observable<AppUser[]> {
    return this.http.get<AppUser[]>(`${environment.apiUrl}/users`);
  }

  update(id: string, request: UpdateUserRequest): Observable<AppUser> {
    return this.http.put<AppUser>(`${environment.apiUrl}/users/${id}`, request);
  }

  approve(id: string): Observable<AppUser> {
    return this.http.put<AppUser>(`${environment.apiUrl}/users/${id}/approve`, {});
  }

  deletionImpact(id: string): Observable<UserDeletionImpact> {
    return this.http.get<UserDeletionImpact>(`${environment.apiUrl}/users/${id}/deletion-impact`);
  }

  /** Suppression définitive du compte, du profil membre et de tout son historique (décision n° 41). */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/users/${id}`);
  }
}
