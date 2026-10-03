import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type StatCardTone = 'gray' | 'orange' | 'brown' | 'blue' | 'green' | 'red' | 'violet';

/**
 * Carte statistique réutilisable (icône, valeur, indication, jauge
 * facultative). Les teintes s'appuient sur la palette AEKD et sur la palette
 * complémentaire bleu/vert/rouge/violet définie dans `src/styles.scss`.
 */
@Component({
  selector: 'app-member-stat-card',
  imports: [MatIconModule],
  template: `
    <article class="stat-card" [class]="'stat-card--' + tone()">
      <div class="stat-card__row">
        <span class="stat-card__icon"><mat-icon>{{ icon() }}</mat-icon></span>
        <div class="stat-card__body">
          <span class="stat-card__title">{{ title() }}</span>
          <span class="stat-card__value">{{ value() }}</span>
          @if (hint()) {
            <span class="stat-card__hint">{{ hint() }}</span>
          }
        </div>
      </div>
      @if (percent() !== null) {
        <span class="stat-card__bar" role="presentation">
          <span [style.width.%]="percent()"></span>
        </span>
      }
    </article>
  `,
  styles: `
    .stat-card {
      --tone: var(--aekd-gray);
      --tone-dark: var(--aekd-gray-dark);
      --tone-soft: #ece8e4;

      position: relative;
      display: flex;
      flex-direction: column;
      gap: 14px;
      height: 100%;
      padding: 18px 20px;
      border-radius: 16px;
      background: #fff;
      border: 1px solid var(--mat-sys-outline-variant);
      box-shadow: var(--aekd-shadow);
      overflow: hidden;

      &::before {
        content: '';
        position: absolute;
        inset: 0 auto 0 0;
        width: 4px;
        background: var(--tone);
      }

      &__row {
        display: flex;
        align-items: flex-start;
        gap: 14px;
      }

      &__icon {
        flex: none;
        display: grid;
        place-items: center;
        width: 46px;
        height: 46px;
        border-radius: 12px;
        background: var(--tone-soft);
        color: var(--tone);
      }

      &__body {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      &__title {
        font-size: 13px;
        font-weight: 600;
        color: var(--aekd-gray-muted);
      }

      &__value {
        margin-top: 2px;
        font-family: 'Poppins', sans-serif;
        font-size: 26px;
        font-weight: 700;
        line-height: 1.2;
        color: var(--aekd-gray);
        overflow-wrap: anywhere;
      }

      &__hint {
        margin-top: 2px;
        font-size: 12.5px;
        font-weight: 600;
        color: var(--tone-dark);
      }

      &__bar {
        display: block;
        height: 6px;
        border-radius: 999px;
        background: var(--tone-soft);
        overflow: hidden;

        span {
          display: block;
          height: 100%;
          border-radius: 999px;
          background: var(--tone);
          transition: width 0.4s ease;
        }
      }

      &--orange {
        --tone: var(--aekd-orange);
        --tone-dark: var(--aekd-orange-dark);
        --tone-soft: var(--aekd-orange-soft);
      }

      &--brown {
        --tone: var(--aekd-brown);
        --tone-dark: var(--aekd-brown-dark);
        --tone-soft: var(--aekd-brown-soft);
      }

      &--blue {
        --tone: var(--aekd-blue);
        --tone-dark: var(--aekd-blue-dark);
        --tone-soft: var(--aekd-blue-soft);
      }

      &--green {
        --tone: var(--aekd-green);
        --tone-dark: var(--aekd-green-dark);
        --tone-soft: var(--aekd-green-soft);
      }

      &--red {
        --tone: var(--aekd-red);
        --tone-dark: var(--aekd-red-dark);
        --tone-soft: var(--aekd-red-soft);
      }

      &--violet {
        --tone: var(--aekd-violet);
        --tone-dark: var(--aekd-violet-dark);
        --tone-soft: var(--aekd-violet-soft);
      }
    }
  `,
})
export class MemberStatCard {
  readonly icon = input.required<string>();
  readonly title = input.required<string>();
  readonly value = input.required<string | number>();
  readonly hint = input<string | null>(null);
  readonly tone = input<StatCardTone>('gray');
  /** Remplissage de la jauge (0–100) ; `null` masque la jauge. */
  readonly percent = input<number | null>(null);
}
