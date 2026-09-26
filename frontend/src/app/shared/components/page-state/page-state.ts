import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export type PageStateKind = 'loading' | 'error' | 'empty';

/**
 * État de page générique (chargement / erreur / aucune donnée), pour arrêter
 * de dupliquer le même bloc `@if (loading()) {...} @else if (error()) {...}`
 * dans chaque écran. Les classes `.state`/`.state--card`/`.state__*` sont
 * globales (voir `src/styles.scss`), ce composant se contente de les utiliser.
 *
 * Utilisation :
 * ```html
 * @if (loading()) {
 *   <app-page-state state="loading" title="Chargement…" />
 * } @else if (error()) {
 *   <app-page-state state="error" icon="cloud_off" title="Impossible de charger les données">
 *     <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Réessayer</button>
 *   </app-page-state>
 * } @else if (items().length === 0) {
 *   <app-page-state state="empty" icon="inbox" title="Aucun résultat" />
 * }
 * ```
 */
@Component({
  selector: 'app-page-state',
  imports: [MatIconModule, MatProgressSpinnerModule],
  template: `
    @if (state() === 'loading') {
      <div class="state"><mat-spinner diameter="40" /></div>
    } @else {
      <div class="state state--card">
        <mat-icon class="state__icon" [class.state__icon--brown]="state() === 'error'">{{ icon() }}</mat-icon>
        <p class="state__title">{{ title() }}</p>
        @if (text()) {
          <p class="state__text">{{ text() }}</p>
        }
        <ng-content />
      </div>
    }
  `,
})
export class PageState {
  readonly state = input.required<PageStateKind>();
  readonly icon = input('info');
  readonly title = input.required<string>();
  readonly text = input<string | null>(null);
}
