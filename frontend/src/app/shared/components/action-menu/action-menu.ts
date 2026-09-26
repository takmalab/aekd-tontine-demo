import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

export interface MenuAction<T = unknown> {
  label: string;
  icon: string;
  action: T;
  disabled?: boolean;
  /** Action sensible (ex. rejeter, supprimer) : rendue en marron plutôt qu'en gris. */
  danger?: boolean;
}

/**
 * Menu d'actions (⋮) réutilisable pour remplacer les groupes de 2-3 boutons
 * empilés en fin de carte quand plusieurs actions sont possibles. Le filtrage
 * des actions selon le rôle reste fait par le composant appelant (via
 * `AuthService`) — ce composant n'affiche que la liste qu'on lui donne.
 *
 * IMPORTANT : ceci est une commodité d'affichage, pas une sécurité. Le
 * backend reste seul responsable de l'autorisation réelle (CLAUDE.md §10).
 */
@Component({
  selector: 'app-action-menu',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button
      mat-icon-button
      type="button"
      [matMenuTriggerFor]="menu"
      [disabled]="disabled()"
      aria-label="Actions"
      (click)="$event.stopPropagation()"
    >
      <mat-icon>more_vert</mat-icon>
    </button>
    <mat-menu #menu="matMenu">
      @for (item of actions(); track item.label) {
        <button mat-menu-item type="button" [disabled]="item.disabled" [class.danger]="item.danger" (click)="selected.emit(item.action)">
          <mat-icon>{{ item.icon }}</mat-icon>
          <span>{{ item.label }}</span>
        </button>
      }
    </mat-menu>
  `,
  styles: `
    .danger {
      color: var(--aekd-brown-dark);

      mat-icon {
        color: var(--aekd-brown);
      }
    }
  `,
})
export class ActionMenu<T = unknown> {
  readonly actions = input.required<MenuAction<T>[]>();
  readonly disabled = input(false);
  readonly selected = output<T>();
}
