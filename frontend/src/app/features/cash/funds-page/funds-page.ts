import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { MatIconModule } from '@angular/material/icon';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { FilterChips, FilterOption } from '../../../shared/components/filter-chips/filter-chips';
import { PageState } from '../../../shared/components/page-state/page-state';
import { AppPagination } from '../../../shared/components/pagination/pagination';
import { formatFcfa } from '../../../shared/utils/format';
import { paginate } from '../../../shared/utils/pagination';
import { ACCOUNT_STYLES, Account, AccountOperation, AccountOperationType, FIGURE_LABELS, FundOverview, MOVEMENT_STYLES } from '../fund.model';
import { FundOverviewApi } from '../fund.service';
import { OperationDialog, OperationDialogData } from '../operation-dialog/operation-dialog';

type FundFilter = 'ALL' | Account;

/**
 * Écran « Fonds » (CLAUDE.md §29, module cash ; ADMIN et TRESORIER) : un solde par
 * compte (decisions.md n° 50), jamais mélangés (§17), et journal des mouvements
 * validés. Aucun calcul métier ici : tout vient du serveur.
 */
@Component({
  selector: 'app-funds-page',
  imports: [DatePipe, MatButtonModule, MatIconModule, ExportMenu, FilterChips, PageState, AppPagination],
  templateUrl: './funds-page.html',
  styleUrl: './funds-page.scss',
})
export class FundsPage implements OnInit {
  private readonly api = inject(FundOverviewApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Dépenses et virements : TRESORIER (ADMIN hérite, decisions.md n° 40 et n° 50). */
  readonly isTreasurer = inject(AuthService).hasRole('TRESORIER');

  readonly formatFcfa = formatFcfa;
  readonly accountStyles = ACCOUNT_STYLES;
  readonly figureLabels = FIGURE_LABELS;
  readonly movementStyles = MOVEMENT_STYLES;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly overview = signal<FundOverview | null>(null);

  readonly filter = signal<FundFilter>('ALL');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(25);

  readonly movements = computed(() => this.overview()?.movements ?? []);
  readonly filtered = computed(() =>
    this.filter() === 'ALL' ? this.movements() : this.movements().filter((m) => m.account === this.filter()),
  );
  readonly paged = computed(() => paginate(this.filtered(), this.pageIndex(), this.pageSize()));
  readonly filterOptions = computed<FilterOption<FundFilter>[]>(() => [
    { value: 'ALL', label: 'Tous', count: this.movements().length },
    ...(this.overview()?.accounts ?? []).map((a) => ({
      value: a.account as FundFilter,
      label: ACCOUNT_STYLES[a.account].label,
      count: this.movements().filter((m) => m.account === a.account).length,
    })),
  ]);
  readonly exportRows = computed(() =>
    this.filtered().map((m) => ({
      Date: m.date ?? '',
      Compte: ACCOUNT_STYLES[m.account].label,
      Type: MOVEMENT_STYLES[m.type].label,
      Libellé: m.label,
      Membre: m.memberFullName ?? '',
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

  openOperation(type: AccountOperationType): void {
    const accounts = this.overview()?.accounts ?? [];
    const data: OperationDialogData = { type, accounts };
    this.dialog
      .open(OperationDialog, { data, width: '560px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((operation?: AccountOperation) => {
        if (operation) {
          this.snackBar.open(type === 'TRANSFER' ? 'Virement enregistré' : 'Dépense enregistrée', 'OK', { duration: 4000 });
          this.load();
        }
      });
  }
}
