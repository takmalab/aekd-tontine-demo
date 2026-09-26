import { Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

/**
 * Barre de recherche réutilisable (filtre client sur une liste déjà chargée).
 * Généralise le champ écrit à la main dans `audit-log.ts`.
 */
@Component({
  selector: 'app-search-bar',
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatIconModule, MatButtonModule],
  template: `
    <mat-form-field appearance="outline" class="search-bar">
      <mat-label>{{ label() }}</mat-label>
      <mat-icon matPrefix class="search-bar__icon">search</mat-icon>
      <input matInput [ngModel]="term()" (ngModelChange)="onChange($event)" [placeholder]="placeholder()" />
      @if (term()) {
        <button matSuffix mat-icon-button type="button" aria-label="Effacer" (click)="onChange('')">
          <mat-icon>close</mat-icon>
        </button>
      }
    </mat-form-field>
  `,
  styles: `
    .search-bar {
      width: 100%;
      max-width: 420px;

      &__icon {
        margin: 0 4px 0 12px;
        color: var(--aekd-gray-muted);
      }
    }
  `,
})
export class SearchBar {
  readonly label = input('Rechercher');
  readonly placeholder = input('');
  /** Valeur initiale/contrôlée depuis le parent (utile pour conserver le terme lors d'une navigation). */
  readonly value = input('');
  readonly valueChange = output<string>();

  readonly term = signal('');

  constructor() {
    effect(() => this.term.set(this.value()));
  }

  onChange(value: string): void {
    this.term.set(value);
    this.valueChange.emit(value);
  }
}
