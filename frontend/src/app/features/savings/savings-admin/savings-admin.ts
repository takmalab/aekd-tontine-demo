import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { FilterChips, FilterOption } from '../../../shared/components/filter-chips/filter-chips';
import { SearchBar } from '../../../shared/components/search-bar/search-bar';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { PAYMENT_OPERATOR_LABELS } from '../../contributions/contribution.model';
import { ProofViewerData, ProofViewerDialog } from '../../payments/proof-viewer-dialog/proof-viewer-dialog';
import {
  MemberSavingsDialog,
  MemberSavingsDialogData,
} from '../member-savings-dialog/member-savings-dialog';
import { RateDialog, RateDialogData } from '../rate-dialog/rate-dialog';
import { SavingsChart } from '../savings-chart/savings-chart';
import {
  SAVINGS_SOURCE_LABELS,
  SAVINGS_STATUS_LABELS,
  SavingsEntry,
  SavingsMemberRow,
  SavingsOverview,
  SavingsRate,
  SavingsStatus,
  formatMonthlyRate,
} from '../savings.model';
import { SavingsService } from '../savings.service';

type Tab = 'overview' | 'savers' | 'history' | 'pending' | 'rates';
type HistoryFilter = 'ALL' | SavingsStatus;

/**
 * « Épargnes » (ADMIN / TRESORIER, docs/decisions.md n° 39) : états généraux,
 * épargnants, historique de tous les mouvements, versements à valider (TRESORIER)
 * et taux d'intérêt mensuel.
 */
@Component({
  selector: 'app-savings-admin',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    ExportMenu,
    FilterChips,
    SearchBar,
    SavingsChart,
  ],
  templateUrl: './savings-admin.html',
  styleUrl: './savings-admin.scss',
})
export class SavingsAdmin implements OnInit {
  private readonly savingsService = inject(SavingsService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Validation / rejet : TRESORIER uniquement. */
  readonly canDecide = this.authService.hasRole('TRESORIER');
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
  readonly formatMonthlyRate = formatMonthlyRate;
  readonly statusLabels = SAVINGS_STATUS_LABELS;
  readonly sourceLabels = SAVINGS_SOURCE_LABELS;
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly overview = signal<SavingsOverview | null>(null);
  readonly entries = signal<SavingsEntry[]>([]);
  readonly rates = signal<SavingsRate[]>([]);
  readonly busyId = signal<string | null>(null);

  readonly tab = signal<Tab>('overview');
  readonly saverSearch = signal('');
  readonly historySearch = signal('');
  readonly historyFilter = signal<HistoryFilter>('ALL');

  /** Versements libres à valider (les cotisations « Banque / Épargne » se valident sur « Paiements »). */
  readonly pending = computed(() =>
    this.entries()
      .filter((e) => e.status === 'PENDING' && e.source === 'DEPOSIT')
      .sort((a, b) => a.declaredAt.localeCompare(b.declaredAt)),
  );

  readonly topSavers = computed(() =>
    (this.overview()?.members ?? []).filter((m) => m.total > 0).slice(0, 5),
  );
  readonly topMax = computed(() => Math.max(1, ...this.topSavers().map((m) => m.total)));

  readonly visibleSavers = computed(() => {
    const term = this.saverSearch().trim().toLowerCase();
    const rows = this.overview()?.members ?? [];
    return term ? rows.filter((r) => r.memberFullName.toLowerCase().includes(term)) : rows;
  });

  readonly historyOptions = computed<FilterOption<HistoryFilter>[]>(() => {
    const all = this.entries();
    const count = (s: SavingsStatus) => all.filter((e) => e.status === s).length;
    return [
      { value: 'ALL', label: 'Tous', count: all.length },
      { value: 'VALIDATED', label: 'Validés', count: count('VALIDATED') },
      { value: 'PENDING', label: 'En attente', count: count('PENDING') },
      { value: 'REJECTED', label: 'Rejetés', count: count('REJECTED') },
    ];
  });

  readonly visibleHistory = computed(() => {
    const f = this.historyFilter();
    const term = this.historySearch().trim().toLowerCase();
    return this.entries().filter(
      (e) =>
        (f === 'ALL' || e.status === f) &&
        (!term ||
          e.memberFullName.toLowerCase().includes(term) ||
          e.reference.toLowerCase().includes(term) ||
          e.label.toLowerCase().includes(term)),
    );
  });

  readonly saverExportRows = computed(() =>
    this.visibleSavers().map((r) => ({
      Membre: r.memberFullName,
      'Épargne versée': formatFcfa(r.principal),
      Intérêts: formatFcfa(r.interest),
      Total: formatFcfa(r.total),
      Versements: r.validatedCount,
      'Dernier versement': r.lastDepositDate ?? '',
      'En attente': formatFcfa(r.pendingAmount),
    })),
  );

  readonly saverExportFilters = computed(() =>
    this.saverSearch().trim() ? `Recherche : « ${this.saverSearch().trim()} »` : null,
  );

  readonly historyExportRows = computed(() =>
    this.visibleHistory().map((e) => ({
      Date: e.paymentDate,
      Membre: e.memberFullName,
      Origine: e.label,
      Montant: formatFcfa(e.amount),
      Opérateur: this.operatorLabels[e.operator],
      Référence: e.reference,
      Statut: this.statusLabels[e.status],
      Intérêts: e.interest === null ? '' : formatFcfa(e.interest),
    })),
  );

  readonly historyExportFilters = computed(() => {
    const parts: string[] = [];
    if (this.historyFilter() !== 'ALL') {
      parts.push(`Statut : ${this.historyOptions().find((o) => o.value === this.historyFilter())?.label}`);
    }
    if (this.historySearch().trim()) {
      parts.push(`Recherche : « ${this.historySearch().trim()} »`);
    }
    return parts.join(' · ') || null;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    forkJoin({
      overview: this.savingsService.overview(),
      entries: this.savingsService.entries(),
      rates: this.savingsService.rates(),
    }).subscribe({
      next: ({ overview, entries, rates }) => {
        this.overview.set(overview);
        this.entries.set(entries);
        this.rates.set([...rates].reverse());
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  openMember(row: SavingsMemberRow): void {
    const data: MemberSavingsDialogData = { memberId: row.memberId, memberFullName: row.memberFullName };
    this.dialog.open(MemberSavingsDialog, { data, width: '680px', maxWidth: 'calc(100vw - 24px)', autoFocus: false });
  }

  viewProof(entry: SavingsEntry): void {
    const data: ProofViewerData = { deposit: entry };
    this.dialog.open(ProofViewerDialog, { data, width: '760px', maxWidth: 'calc(100vw - 24px)', autoFocus: false });
  }

  setRate(): void {
    const data: RateDialogData = { currentRate: this.overview()?.currentMonthlyRate ?? null };
    this.dialog
      .open(RateDialog, { data, width: '560px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((rate?: SavingsRate) => {
        if (rate) {
          this.snackBar.open(`Taux enregistré : ${formatMonthlyRate(rate.monthlyRate)}`, 'OK', { duration: 4000 });
          this.load();
        }
      });
  }

  decide(entry: SavingsEntry, decision: 'validate' | 'reject'): void {
    const amount = formatFcfa(entry.amount);
    const data: ConfirmDialogData =
      decision === 'validate'
        ? {
            title: 'Valider ce versement ?',
            message: `${amount} de ${entry.memberFullName} sera ajouté à son épargne et produira des intérêts à partir d'aujourd'hui. Cette action est définitive.`,
            confirmLabel: 'Valider',
            icon: 'task_alt',
          }
        : {
            title: 'Rejeter ce versement ?',
            message: `Le versement de ${amount} déclaré par ${entry.memberFullName} ne sera pas ajouté à son épargne.`,
            confirmLabel: 'Rejeter',
            icon: 'block',
          };
    this.dialog
      .open(ConfirmDialog, { data, width: '440px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busyId.set(entry.id);
        const call = decision === 'validate' ? this.savingsService.validate(entry.id) : this.savingsService.reject(entry.id);
        call.subscribe({
          next: () => {
            this.busyId.set(null);
            this.snackBar.open(decision === 'validate' ? 'Versement validé' : 'Versement rejeté', 'OK', {
              duration: 4000,
            });
            this.load();
          },
          error: (err: HttpErrorResponse) => {
            this.busyId.set(null);
            this.snackBar.open(
              err.status === 400 || err.status === 403
                ? err.error?.message || "L'opération a été refusée."
                : "L'opération a échoué. Vérifiez que le serveur est démarré.",
              'OK',
              { duration: 7000 },
            );
          },
        });
      });
  }
}
