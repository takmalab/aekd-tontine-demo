import { Component, computed, input, signal } from '@angular/core';
import { formatFcfa } from '../../../shared/utils/format';
import { SavingsMonthlyPoint } from '../savings.model';

interface Bar {
  point: SavingsMonthlyPoint;
  label: string;
  longLabel: string;
  x: number;
  y: number;
  height: number;
  path: string;
}

const WIDTH = 640;
const HEIGHT = 220;
const PAD = { top: 12, right: 8, bottom: 26, left: 64 };
const GAP = 2;
const RADIUS = 4;

/**
 * Épargne validée par mois (une seule série : pas de légende, le titre la nomme).
 * Barres fines à extrémité arrondie posées sur la ligne de base, grille
 * discrète, infobulle au survol, tableau équivalent pour les lecteurs d'écran.
 */
@Component({
  selector: 'app-savings-chart',
  template: `
    <div class="chart" (mouseleave)="hovered.set(null)">
      <svg
        [attr.viewBox]="'0 0 ' + width + ' ' + height"
        role="img"
        [attr.aria-label]="ariaLabel()"
      >
        @for (tick of ticks(); track tick.value) {
          <line class="grid" [attr.x1]="pad.left" [attr.x2]="width - pad.right" [attr.y1]="tick.y" [attr.y2]="tick.y" />
          <text class="axis" [attr.x]="pad.left - 8" [attr.y]="tick.y + 4" text-anchor="end">{{ tick.label }}</text>
        }
        <line class="baseline" [attr.x1]="pad.left" [attr.x2]="width - pad.right" [attr.y1]="baseY" [attr.y2]="baseY" />
        @for (bar of bars(); track bar.point.month; let i = $index) {
          <!-- Zone de survol pleine hauteur, plus large que la barre -->
          <rect
            class="hit"
            [attr.x]="bar.x - slotGap() / 2"
            [attr.y]="pad.top"
            [attr.width]="barWidth() + slotGap()"
            [attr.height]="baseY - pad.top"
            (mouseenter)="hovered.set(i)"
          />
          @if (bar.height > 0) {
            <path class="bar" [class.bar--dim]="hovered() !== null && hovered() !== i" [attr.d]="bar.path" />
          }
          <text class="axis" [attr.x]="bar.x + barWidth() / 2" [attr.y]="height - 8" text-anchor="middle">
            {{ bar.label }}
          </text>
        }
      </svg>

      @if (hoveredBar(); as bar) {
        <div class="tooltip" [style.left.%]="((bar.x + barWidth() / 2) / width) * 100">
          <span class="tooltip__title">{{ bar.longLabel }}</span>
          <span class="tooltip__value">{{ formatFcfa(bar.point.amount) }}</span>
          <span class="tooltip__meta">{{ bar.point.count }} versement{{ bar.point.count > 1 ? 's' : '' }} validé{{ bar.point.count > 1 ? 's' : '' }}</span>
        </div>
      }

      <table class="sr-only">
        <caption>Épargne validée par mois</caption>
        <thead><tr><th>Mois</th><th>Montant</th><th>Versements</th></tr></thead>
        <tbody>
          @for (bar of bars(); track bar.point.month) {
            <tr><td>{{ bar.longLabel }}</td><td>{{ formatFcfa(bar.point.amount) }}</td><td>{{ bar.point.count }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `
    .chart {
      position: relative;
      width: 100%;
    }
    svg {
      display: block;
      width: 100%;
      height: auto;
      overflow: visible;
    }
    .grid {
      stroke: var(--mat-sys-outline-variant);
      stroke-width: 1;
      stroke-dasharray: 2 4;
    }
    .baseline {
      stroke: var(--aekd-gray-muted);
      stroke-width: 1;
    }
    .axis {
      fill: var(--aekd-gray-muted);
      font-size: 11px;
    }
    .bar {
      fill: var(--aekd-orange);
      transition: opacity 0.15s ease;
    }
    .bar--dim {
      opacity: 0.45;
    }
    .hit {
      fill: transparent;
      cursor: default;
    }
    .tooltip {
      position: absolute;
      top: 0;
      transform: translate(-50%, -8px);
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 8px 10px;
      border-radius: 10px;
      background: var(--aekd-gray);
      color: #fff;
      font-size: 12px;
      white-space: nowrap;
      pointer-events: none;
      box-shadow: var(--aekd-shadow-lg);
    }
    .tooltip__title {
      opacity: 0.8;
      text-transform: capitalize;
    }
    .tooltip__value {
      font-weight: 700;
      font-size: 14px;
    }
    .tooltip__meta {
      opacity: 0.8;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class SavingsChart {
  readonly points = input.required<SavingsMonthlyPoint[]>();

  readonly width = WIDTH;
  readonly height = HEIGHT;
  readonly pad = PAD;
  readonly baseY = HEIGHT - PAD.bottom;
  readonly formatFcfa = formatFcfa;
  readonly hovered = signal<number | null>(null);

  private readonly max = computed(() => niceMax(Math.max(0, ...this.points().map((p) => p.amount))));

  readonly slotGap = computed(() => Math.max(GAP, this.slot() * 0.35));
  private readonly slot = computed(() => (WIDTH - PAD.left - PAD.right) / Math.max(1, this.points().length));
  readonly barWidth = computed(() => Math.max(4, this.slot() - this.slotGap()));

  readonly ticks = computed(() => {
    const max = this.max();
    return [0.25, 0.5, 0.75, 1].map((f) => ({
      value: max * f,
      y: this.baseY - (this.baseY - PAD.top) * f,
      label: compact(max * f),
    }));
  });

  readonly bars = computed<Bar[]>(() => {
    const max = this.max();
    const plot = this.baseY - PAD.top;
    return this.points().map((point, i) => {
      const x = PAD.left + i * this.slot() + this.slotGap() / 2;
      const height = max > 0 ? (point.amount / max) * plot : 0;
      const y = this.baseY - height;
      const date = new Date(`${point.month}-01T00:00:00`);
      return {
        point,
        x,
        y,
        height,
        label: date.toLocaleDateString('fr-FR', { month: 'short' }).replace('.', ''),
        longLabel: date.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }),
        path: roundedTop(x, y, this.barWidth(), height, Math.min(RADIUS, height, this.barWidth() / 2)),
      };
    });
  });

  readonly hoveredBar = computed(() => {
    const i = this.hovered();
    return i === null ? null : (this.bars()[i] ?? null);
  });

  readonly ariaLabel = computed(
    () =>
      'Épargne validée par mois : ' +
      this.bars()
        .map((b) => `${b.longLabel} ${formatFcfa(b.point.amount)}`)
        .join(', '),
  );
}

/** Barre posée sur la ligne de base, coins supérieurs arrondis. */
function roundedTop(x: number, y: number, w: number, h: number, r: number): string {
  if (h <= 0) {
    return '';
  }
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`;
}

/** Maximum « rond » de l'axe (1, 2, 2,5, 5 × 10ⁿ). */
function niceMax(value: number): number {
  if (value <= 0) {
    return 1000;
  }
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 2.5, 5, 10].find((s) => s * magnitude >= value) ?? 10;
  return step * magnitude;
}

/** « 150 k », « 1,2 M » pour les graduations. */
function compact(value: number): string {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} M`;
  }
  if (value >= 1000) {
    return `${(value / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} k`;
  }
  return value.toLocaleString('fr-FR');
}
