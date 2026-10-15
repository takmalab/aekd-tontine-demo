import { HttpClient } from '@angular/common/http';
import { Injectable, Signal, WritableSignal, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Photos de profil des membres, visibles par tous les utilisateurs connectés.
 * Chaque photo n'est demandée qu'une fois (l'absence de photo aussi) puis gardée
 * en mémoire le temps de la session : moins de 30 membres (regles-metier.md §2).
 */
@Injectable({ providedIn: 'root' })
export class MemberPhotoService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, WritableSignal<string | null>>();

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
      this.http.get(`${environment.apiUrl}/members/${memberId}/photo`, { responseType: 'blob' }).subscribe({
        next: (blob) => created.set(URL.createObjectURL(blob)),
        error: () => created.set(null),
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
  }
}
