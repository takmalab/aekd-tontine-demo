import { DatePipe, LowerCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
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
import { ContributionService } from '../contribution.service';

interface ContributionRow {
  definition: ContributionDefinition;
  periods: ContributionPeriod[];
}

type StatusFilter = 'ALL' | ContributionStatus;

@Component({
  selector: 'app-contributions-list',
  imports: [RouterLink, DatePipe, LowerCasePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './contributions-list.html',
  styleUrl: './contributions-list.scss',
})
export class ContributionsList implements OnInit {
  private readonly contributionService = inject(ContributionService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

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
  readonly activatingId = signal<string | null>(null);

  readonly filters: { value: StatusFilter; label: string }[] = [
    { value: 'ALL', label: 'Toutes' },
    { value: 'ACTIVE', label: 'Actives' },
    { value: 'DRAFT', label: 'Brouillons' },
  ];

  readonly counts = computed(() => {
    const all = this.rows();
    return {
      ALL: all.length,
      ACTIVE: all.filter((r) => r.definition.status === 'ACTIVE').length,
      DRAFT: all.filter((r) => r.definition.status === 'DRAFT').length,
      INACTIVE: all.filter((r) => r.definition.status === 'INACTIVE').length,
    } as Record<StatusFilter, number>;
  });

  readonly visibleRows = computed(() => {
    const f = this.filter();
    return f === 'ALL' ? this.rows() : this.rows().filter((r) => r.definition.status === f);
  });

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

  activate(row: ContributionRow): void {
    const data: ConfirmDialogData = {
      title: 'Activer la cotisation ?',
      message: `« ${row.definition.name} » deviendra visible selon sa visibilité et ses participants pourront déclarer leurs paiements.`,
      confirmLabel: 'Activer',
      icon: 'rocket_launch',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '440px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.activatingId.set(row.definition.id);
        this.contributionService.activate(row.definition.id).subscribe({
          next: (updated) => {
            this.activatingId.set(null);
            this.rows.update((rows) =>
              rows.map((r) => (r.definition.id === updated.id ? { ...r, definition: updated } : r)),
            );
            this.snackBar.open(`Cotisation « ${updated.name} » activée`, 'OK', { duration: 4000 });
          },
          error: (err: HttpErrorResponse) => {
            this.activatingId.set(null);
            this.snackBar.open(
              err.status === 400 ? 'Cette cotisation est déjà active.' : "L'activation a échoué.",
              'OK',
              { duration: 5000 },
            );
          },
        });
      });
  }
}
