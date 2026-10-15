import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { FilterChips, FilterOption } from '../../../shared/components/filter-chips/filter-chips';
import { PageState } from '../../../shared/components/page-state/page-state';
import { AppPagination } from '../../../shared/components/pagination/pagination';
import { formatFcfa } from '../../../shared/utils/format';
import { paginate } from '../../../shared/utils/pagination';
import { FUND_LABELS, Fund, FundOverview, MOVEMENT_STYLES } from '../fund.model';
import { FundOverviewApi } from '../fund.service';

type FundFilter = 'ALL' | Fund;

/**
 * Écran « Fonds » (CLAUDE.md §29, module cash ; ADMIN et TRESORIER) : soldes du
 * fonds commun et de l'épargne, toujours présentés séparément (§17), et journal
 * des mouvements validés. Aucun calcul métier ici : tout vient du serveur.
 */
@Component({
  selector: 'app-funds-page',
  imports: [DatePipe, MatButtonModule, MatIconModule, ExportMenu, FilterChips, PageState, AppPagination],
  templateUrl: './funds-page.html',
  styleUrl: './funds-page.scss',
})
export class FundsPage implements OnInit {
  private readonly api = inject(FundOverviewApi);

  readonly formatFcfa = formatFcfa;
  readonly fundLabels = FUND_LABELS;
  readonly movementStyles = MOVEMENT_STYLES;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly overview = signal<FundOverview | null>(null);

  readonly filter = signal<FundFilter>('ALL');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(25);

  readonly movements = computed(() => this.overview()?.movements ?? []);
  readonly filtered = computed(() =>
    this.filter() === 'ALL' ? this.movements() : this.movements().filter((m) => m.fund === this.filter()),
  );
  readonly paged = computed(() => paginate(this.filtered(), this.pageIndex(), this.pageSize()));
  readonly filterOptions = computed<FilterOption<FundFilter>[]>(() => [
    { value: 'ALL', label: 'Tous', count: this.movements().length },
    { value: 'TONTINE_FUND', label: 'Fonds commun', count: this.movements().filter((m) => m.fund === 'TONTINE_FUND').length },
    { value: 'SAVINGS', label: 'Épargne', count: this.movements().filter((m) => m.fund === 'SAVINGS').length },
  ]);
  readonly exportRows = computed(() =>
    this.filtered().map((m) => ({
      Date: m.date ?? '',
      Fonds: FUND_LABELS[m.fund],
      Type: MOVEMENT_STYLES[m.type].label,
      Libellé: m.label,
      Membre: m.memberFullName,
      Entrée: m.direction === 'IN' ? formatFcfa(m.amount) : '',
      Sortie: m.direction === 'OUT' ? formatFcfa(m.amount) : '',
    })),
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api.overview().subscribe({
      next: (overview) => {
        this.overview.set(overview);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  setFilter(filter: FundFilter): void {
    this.filter.set(filter);
    this.pageIndex.set(0);
  }
}
