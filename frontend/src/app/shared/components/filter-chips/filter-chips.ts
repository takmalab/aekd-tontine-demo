import { Component, input, output } from '@angular/core';

export interface FilterOption<T = string> {
  value: T;
  label: string;
  count: number;
}

/**
 * Puces de filtre avec compteur, cliquables. Généralise le motif déjà écrit à
 * la main dans `contribution-detail.html` et `staff-sanctions.html` (classe
 * `.filter`/`.filter--active`/`.filter__count` définie dans `_loans.scss`,
 * réutilisée par convention pour ce pattern dans tout le projet).
 */
@Component({
  selector: 'app-filter-chips',
  template: `
    <div class="filters" role="tablist" [attr.aria-label]="ariaLabel()">
      @for (option of options(); track option.value) {
        <button
          type="button"
          role="tab"
          class="filter"
          [class.filter--active]="option.value === active()"
          [attr.aria-selected]="option.value === active()"
          (click)="activeChange.emit(option.value)"
        >
          {{ option.label }}
          <span class="filter__count">{{ option.count }}</span>
        </button>
      }
    </div>
  `,
  styles: `
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 18px;
    }

    .filter {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      height: 36px;
      padding: 0 14px;
      border-radius: 999px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: #fff;
      color: var(--aekd-gray);
      font: inherit;
      font-weight: 600;
      font-size: 13.5px;
      cursor: pointer;

      &:hover {
        border-color: var(--aekd-orange);
      }

      &:focus-visible {
        outline: 2px solid var(--aekd-orange);
        outline-offset: 2px;
      }

      &__count {
        display: inline-grid;
        place-items: center;
        min-width: 22px;
        height: 22px;
        padding: 0 6px;
        border-radius: 999px;
        background: #ece8e4;
        font-size: 12px;
      }

      &--active {
        background: var(--aekd-gray);
        border-color: var(--aekd-gray);
        color: #fff;

        .filter__count {
          background: var(--aekd-orange);
          color: #fff;
        }
      }
    }
  `,
})
export class FilterChips<T = string> {
  readonly options = input.required<FilterOption<T>[]>();
  readonly active = input.required<T>();
  readonly ariaLabel = input('Filtrer');
  readonly activeChange = output<T>();
}
