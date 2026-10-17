import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, WritableSignal, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Photos de profil des membres, visibles par tous les utilisateurs connectés.
 * La liste des membres qui ont une photo est lue une fois (T2) : seules ces photos sont
 * demandées, puis gardées en mémoire le temps de la session — moins de 30 membres
 * (regles-metier.md §2). Un membre sans photo ne déclenche plus de requête en 404.
 */
@Injectable({ providedIn: 'root' })
export class MemberPhotoService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, WritableSignal<string | null>>();
  private withPhoto$: Observable<Set<string>> | null = null;

  /** URL locale de la photo d'un membre, ou null (pas de photo, ou pas encore chargée). */
  url(memberId: string | null | undefined): string | null {
    return memberId ? this.photo(memberId)() : null;
  }

  photo(memberId: string): Signal<string | null> {
    let entry = this.cache.get(memberId);
    if (!entry) {
      const created = signal<string | null>(null);
      entry = created;
      this.cache.set(memberId, created);
      this.membersWithPhoto().subscribe((ids) => {
        if (!ids.has(memberId)) {
          return;
        }
        this.http.get(`${environment.apiUrl}/members/${memberId}/photo`, { responseType: 'blob' }).subscribe({
          next: (blob) => created.set(URL.createObjectURL(blob)),
          error: () => created.set(null),
        });
      });
    }
    return entry;
  }

  /** Après un changement de photo ou une déconnexion : tout sera relu. */
  clear(): void {
    for (const entry of this.cache.values()) {
      const url = entry();
      if (url) {
        URL.revokeObjectURL(url);
      }
    }
    this.cache.clear();
    this.withPhoto$ = null;
  }

  private membersWithPhoto(): Observable<Set<string>> {
    if (!this.withPhoto$) {
      this.withPhoto$ = this.http.get<string[]>(`${environment.apiUrl}/members/photos`).pipe(
        catchError(() => of([] as string[])),
        map((ids) => new Set(ids)),
        // Ensemble partagé par toutes les vignettes de la page.
        shareReplay(1),
      );
    }
    return this.withPhoto$;
  }
}
