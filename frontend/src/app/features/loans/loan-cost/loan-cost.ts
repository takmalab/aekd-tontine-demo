import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { formatFcfa } from '../../../shared/utils/format';
import { loanInterest } from '../loan-interest';

/**
 * Coût d'un prêt : capital, intérêts et total à rembourser (docs/decisions.md
 * n° 14 et 19). Utilisé à la demande, à la décision et sur la carte du prêt.
 */
@Component({
  selector: 'app-loan-cost',
  imports: [MatIconModule],
  templateUrl: './loan-cost.html',
  styleUrl: './loan-cost.scss',
})
export class LoanCost {
  readonly capital = input<number | null>(null);
  /** Taux mensuel en %. */
  readonly rate = input<number | null>(null);
  readonly months = input<number | null>(null);
  /** Intérêts déjà calculés par le serveur ; à défaut, calculés ici. */
  readonly interestAmount = input<number | null>(null);
  /** Vrai tant que le prêt n'est pas approuvé : montants indicatifs. */
  readonly estimated = input(true);

  readonly formatFcfa = formatFcfa;
  readonly interest = computed(
    () => this.interestAmount() ?? loanInterest(this.capital(), this.rate(), this.months()),
  );
  readonly total = computed(() => (this.capital() ?? 0) + this.interest());
  readonly rateLabel = computed(() => String(this.rate() ?? 0).replace('.', ','));
}
