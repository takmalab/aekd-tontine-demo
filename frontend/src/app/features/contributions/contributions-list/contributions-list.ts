import { DatePipe, LowerCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { catchError, finalize, forkJoin, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ActionMenu, MenuAction } from '../../../shared/components/action-menu/action-menu';
import { FilterChips, FilterOption } from '../../../shared/components/filter-chips/filter-chips';
import { PageState } from '../../../shared/components/page-state/page-state';
import { SearchBar } from '../../../shared/components/search-bar/search-bar';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import {
  AMOUNT_MODE_LABELS,
  CONTRIBUTION_STATUS_LABELS,
  ContributionDefinition,
  ContributionPeriod,
  ContributionStatus,
  FREQUENCY_LABELS,
  FUND_DESTINATION_LABELS,
} from '../contribution.model';
import { ContributionActions } from '../contribution-actions';
import { ContributionService } from '../contribution.service';

interface ContributionRow {
  definition: ContributionDefinition;
  periods: ContributionPeriod[];
}

type StatusFilter = 'ALL' | ContributionStatus;
type SortKey = 'default' | 'name' | 'amount';
type RowAction = 'edit' | 'activate' | 'deactivate' | 'delete';

@Component({
  selector: 'app-contributions-list',
  imports: [
    RouterLink,
    DatePipe,
    LowerCasePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    PageState,
    SearchBar,
    FilterChips,
    ActionMenu,
  ],
  templateUrl: './contributions-list.html',
  styleUrl: './contributions-list.scss',
})
export class ContributionsList implements OnInit {
  private readonly contributionService = inject(ContributionService);
  private readonly authService = inject(AuthService);
  private readonly actions = inject(ContributionActions);

  readonly canManage = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);
  readonly statusLabels = CONTRIBUTION_STATUS_LABELS;
  readonly destinationLabels = FUND_DESTINATION_LABELS;
  readonly frequencyLabels = FREQUENCY_LABELS;
  readonly amountModeLabels = AMOUNT_MODE_LABELS;
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly rows = signal<ContributionRow[]>([]);
  readonly filter = signal<StatusFilter>('ALL');
  readonly search = signal('');
  readonly sortBy = signal<SortKey>('default');
  readonly activatingId = signal<string | null>(null);

  readonly statusOptions = computed<FilterOption<StatusFilter>[]>(() => {
    const all = this.rows();
    return [
      { value: 'ALL', label: 'Toutes', count: all.length },
      { value: 'ACTIVE', label: 'Actives', count: all.filter((r) => r.definition.status === 'ACTIVE').length },
      { value: 'DRAFT', label: 'Brouillons', count: all.filter((r) => r.definition.status === 'DRAFT').length },
    ];
  });

  readonly visibleRows = computed(() => {
    const f = this.filter();
    const term = this.search().trim().toLowerCase();
    let list = f === 'ALL' ? this.rows() : this.rows().filter((r) => r.definition.status === f);
    if (term) {
      list = list.filter((r) => r.definition.name.toLowerCase().includes(term));
    }

    const sortKey = this.sortBy();
    if (sortKey === 'default') {
      return list; // Ordre du backend : obligatoires d'abord, montant décroissant, nom.
    }
    return [...list].sort((a, b) =>
      sortKey === 'name'
        ? a.definition.name.localeCompare(b.definition.name, 'fr')
        : (b.definition.amount ?? -1) - (a.definition.amount ?? -1),
    );
  });

  onSearch(term: string): void {
    this.search.set(term);
  }

  onSortChange(key: SortKey): void {
    this.sortBy.set(key);
  }

  ngOnInit(): void {
    this.load();
  }

  /** Cotisations visibles + leurs séances de rattachement (un appel par cotisation, suffisant pour le MVP). */
  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.contributionService
      .list()
      .pipe(
        switchMap((definitions) =>
          definitions.length === 0
            ? of([] as ContributionRow[])
            : forkJoin(
                definitions.map((definition) =>
                  this.contributionService.listPeriods(definition.id).pipe(
                    catchError(() => of([] as ContributionPeriod[])),
                    map((periods) => ({ definition, periods })),
                  ),
                ),
              ),
        ),
      )
      .subscribe({
        next: (rows) => {
          // Obligatoires d'abord, puis montant décroissant, puis nom.
          rows.sort(
            (a, b) =>
              Number(b.definition.mandatory) - Number(a.definition.mandatory) ||
              (b.definition.amount ?? -1) - (a.definition.amount ?? -1) ||
              a.definition.name.localeCompare(b.definition.name, 'fr'),
          );
          this.rows.set(rows);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }

  /** Actions du menu ⋮ selon le statut ; suppression grisée dès qu'un paiement existe. */
  menuActions(definition: ContributionDefinition): MenuAction<RowAction>[] {
    const items: MenuAction<RowAction>[] = [{ label: 'Modifier', icon: 'edit', action: 'edit' }];
    if (definition.status === 'ACTIVE') {
      items.push({ label: 'Désactiver', icon: 'pause_circle', action: 'deactivate' });
    } else {
      items.push({
        label: definition.status === 'INACTIVE' ? 'Réactiver' : 'Activer',
        icon: 'rocket_launch',
        action: 'activate',
      });
    }
    items.push({
      label: definition.hasPayments ? 'Supprimer (paiements existants)' : 'Supprimer',
      icon: 'delete',
      action: 'delete',
      danger: true,
      disabled: definition.hasPayments,
    });
    return items;
  }

  onAction(row: ContributionRow, action: RowAction): void {
    const definition = row.definition;
    if (action === 'delete') {
      this.actions.delete(definition).subscribe((id) =>
        this.rows.update((rows) => rows.filter((r) => r.definition.id !== id)),
      );
      return;
    }
    const update$ =
      action === 'edit'
        ? this.actions.edit(definition)
        : action === 'deactivate'
          ? this.actions.deactivate(definition)
          : this.actions.activate(definition);
    this.activatingId.set(action === 'activate' ? definition.id : null);
    update$.pipe(finalize(() => this.activatingId.set(null))).subscribe((updated) =>
      this.rows.update((rows) =>
        rows.map((r) => (r.definition.id === updated.id ? { ...r, definition: updated } : r)),
      ),
    );
  }
}
