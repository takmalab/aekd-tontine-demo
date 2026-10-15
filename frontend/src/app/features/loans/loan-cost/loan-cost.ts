import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { formatFcfa } from '../../../shared/utils/format';
import { loanInterest } from '../loan-interest';
import { interestPeriodLabel } from '../loan.model';

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
  /** Taux en %, par période de `period` mois. */
  readonly rate = input<number | null>(null);
  /** Période du taux en mois (decisions.md n° 53). */
  readonly period = input<number | null>(1);
  readonly months = input<number | null>(null);
  /** Intérêts déjà calculés par le serveur ; à défaut, calculés ici. */
  readonly interestAmount = input<number | null>(null);
  /** Vrai tant que le prêt n'est pas approuvé : montants indicatifs. */
  readonly estimated = input(true);

  readonly formatFcfa = formatFcfa;
  readonly interest = computed(
    () => this.interestAmount() ?? loanInterest(this.capital(), this.rate(), this.months(), this.period()),
  );
  readonly total = computed(() => (this.capital() ?? 0) + this.interest());
  readonly rateLabel = computed(() => String(this.rate() ?? 0).replace('.', ','));
  readonly periodLabel = computed(() => interestPeriodLabel(this.period()));
}
