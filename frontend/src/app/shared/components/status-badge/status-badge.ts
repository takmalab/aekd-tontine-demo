import { Component, input } from '@angular/core';

/**
 * Variantes disponibles via la classe globale `.status--<variant>`
 * (voir `shared/styles/_page.scss`, incluse globalement dans `src/styles.scss`).
 */
export type StatusVariant = 'paid' | 'pending' | 'partial' | 'late' | 'not_paid';

/**
 * Badge de statut réutilisable, pour remplacer les `[class.status--xxx]`
 * répétés dans chaque écran (membres, utilisateurs, paiements…).
 */
@Component({
  selector: 'app-status-badge',
  template: `<span class="status" [class]="'status--' + variant()">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly variant = input.required<StatusVariant>();
  readonly label = input.required<string>();
}
