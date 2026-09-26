import { DatePipe, LowerCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, forkJoin, of } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { formatFcfa, fromIsoDate, toIsoDate } from '../../../shared/utils/format';
import { Member } from '../../members/member.model';
import { MemberService } from '../../members/member.service';
import {
  DeclarePaymentDialog,
  DeclarePaymentData,
} from '../../payments/declare-payment-dialog/declare-payment-dialog';
import { canDeclare, displayedPeriodStatus } from '../../payments/period-status';
import { ProofUploadDialog, ProofUploadData } from '../../payments/proof-upload-dialog/proof-upload-dialog';
import { Session } from '../../sessions/session.model';
import { SessionService } from '../../sessions/session.service';
import {
  AMOUNT_MODE_LABELS,
  CONTRIBUTION_STATUS_LABELS,
  ContributionDefinition,
  ContributionPeriod,
  ContributionPeriodStatus,
  ContributionTransaction,
  FREQUENCY_LABELS,
  FUND_DESTINATION_LABELS,
  ParticipantPaymentStatus,
  VISIBILITY_LABELS,
} from '../contribution.model';
import { ContributionService } from '../contribution.service';

/** Filtres demandés : Tous / Payé / Non payé / Partiel (« en retard » est compté dans « Non payé »). */
type ParticipantFilter = 'ALL' | 'PAID' | 'UNPAID' | 'PARTIAL';

interface PeriodOption {
  period: ContributionPeriod;
  session: Session | null;
}

interface ParticipantRow extends ParticipantPaymentStatus {
  /** Statut affiché (une cotisation facultative n'est jamais « en retard »). */
  shown: ContributionPeriodStatus;
  isMe: boolean;
}

const PARTICIPANT_STATUS_LABELS: Record<ContributionPeriodStatus, string> = {
  PAID: 'Payé',
  PENDING: 'En attente',
  PARTIAL: 'Partiel',
  LATE: 'Non payé (en retard)',
  NOT_PAID: 'Non payé',
};

function filterOf(status: ContributionPeriodStatus): ParticipantFilter | null {
  switch (status) {
    case 'PAID':
      return 'PAID';
    case 'PARTIAL':
      return 'PARTIAL';
    case 'LATE':
    case 'NOT_PAID':
      return 'UNPAID';
    default:
      return null; // PENDING : visible dans « Tous »
  }
}

/**
 * Détail d'une cotisation : informations, participants et situation de paiement
 * de chacun pour une séance, filtrable ; bouton « Cotiser » pour le membre courant
 * s'il est participant et peut déclarer (paiements partiels autorisés).
 */
@Component({
  selector: 'app-contribution-detail',
  imports: [
    RouterLink,
    DatePipe,
    LowerCasePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  templateUrl: './contribution-detail.html',
  styleUrl: './contribution-detail.scss',
})
export class ContributionDetail implements OnInit {
  private readonly contributionService = inject(ContributionService);
  private readonly sessionService = inject(SessionService);
  private readonly memberService = inject(MemberService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly authService = inject(AuthService);

  /** Paramètre de route `:id`. */
  readonly id = input.required<string>();

  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;
  readonly statusLabels = CONTRIBUTION_STATUS_LABELS;
  readonly destinationLabels = FUND_DESTINATION_LABELS;
  readonly visibilityLabels = VISIBILITY_LABELS;
  readonly frequencyLabels = FREQUENCY_LABELS;
  readonly amountModeLabels = AMOUNT_MODE_LABELS;
  readonly participantLabels = PARTICIPANT_STATUS_LABELS;
  /** Lien vers la gestion des sanctions de la cotisation (ADMIN / TRESORIER). */
  readonly isStaff = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);

  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly error = signal(false);
  readonly definition = signal<ContributionDefinition | null>(null);
  readonly periods = signal<PeriodOption[]>([]);
  readonly me = signal<Member | null>(null);

  readonly selectedPeriodId = signal<string | null>(null);
  readonly statusesLoading = signal(false);
  readonly statusesError = signal(false);
  readonly statuses = signal<ParticipantPaymentStatus[]>([]);
  readonly filter = signal<ParticipantFilter>('ALL');

  readonly filters: { value: ParticipantFilter; label: string }[] = [
    { value: 'ALL', label: 'Tous' },
    { value: 'PAID', label: 'Payé' },
    { value: 'UNPAID', label: 'Non payé' },
    { value: 'PARTIAL', label: 'Partiel' },
  ];

  readonly selectedPeriod = computed(
    () => this.periods().find((p) => p.period.id === this.selectedPeriodId()) ?? null,
  );

  readonly rows = computed<ParticipantRow[]>(() => {
    const mandatory = this.definition()?.mandatory ?? true;
    const meId = this.me()?.id;
    return this.statuses().map((s) => ({
      ...s,
      shown: displayedPeriodStatus(s.status, mandatory),
      isMe: s.memberId === meId,
    }));
  });

  readonly counts = computed(() => {
    const c: Record<ParticipantFilter, number> = { ALL: 0, PAID: 0, UNPAID: 0, PARTIAL: 0 };
    for (const row of this.rows()) {
      c.ALL++;
      const f = filterOf(row.shown);
      if (f) {
        c[f]++;
      }
    }
    return c;
  });

  readonly pendingCount = computed(() => this.rows().filter((r) => r.shown === 'PENDING').length);

  readonly visibleRows = computed(() => {
    const f = this.filter();
    const list = f === 'ALL' ? this.rows() : this.rows().filter((r) => filterOf(r.shown) === f);
    // Le membre courant en tête, puis ordre alphabétique (déjà trié par le backend).
    return [...list].sort((a, b) => Number(b.isMe) - Number(a.isMe));
  });

  readonly totals = computed(() => {
    const rows = this.rows();
    const collected = rows.reduce((sum, r) => sum + r.validatedAmount, 0);
    const def = this.definition();
    const expected = def?.amountMode === 'FIXED' && def.amount !== null ? def.amount * rows.length : null;
    return { collected, expected };
  });

  readonly myRow = computed(() => this.rows().find((r) => r.isMe) ?? null);

  /** « Cotiser » : membre participant, cotisation active, rien en attente et pas encore entièrement payé. */
  readonly canContribute = computed(() => {
    const mine = this.myRow();
    return this.definition()?.status === 'ACTIVE' && !!mine && canDeclare(mine.shown);
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(false);
    forkJoin({
      definition: this.contributionService.get(this.id()),
      periods: this.contributionService.listPeriods(this.id()),
      sessions: this.sessionService.list().pipe(catchError(() => of([] as Session[]))),
      // Comptes sans profil membre (ex. administrateur) : pas de bouton « Cotiser ».
      me: this.memberService.mine().pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ definition, periods, sessions, me }) => {
        const byId = new Map(sessions.map((s) => [s.id, s]));
        const options = periods
          .map((period) => ({ period, session: byId.get(period.sessionId) ?? null }))
          .sort((a, b) => (b.session?.date ?? '').localeCompare(a.session?.date ?? ''));
        this.definition.set(definition);
        this.periods.set(options);
        this.me.set(me);
        this.loading.set(false);
        const initial = this.defaultPeriod(options);
        if (initial) {
          this.selectPeriod(initial.period.id);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.notFound.set(err.status === 404 || err.status === 400);
        this.error.set(!(err.status === 404 || err.status === 400));
        this.loading.set(false);
      },
    });
  }

  /**
   * Séance affichée par défaut : la plus récente déjà tenue (date ≤ aujourd'hui),
   * sinon la prochaine à venir — même logique que la « séance actuelle » du tableau de bord.
   */
  private defaultPeriod(options: PeriodOption[]): PeriodOption | null {
    const today = toIsoDate(new Date());
    const held = options.filter((o) => o.session && o.session.date <= today); // déjà triées, plus récente d'abord
    if (held.length) {
      return held[0];
    }
    const upcoming = options
      .filter((o) => o.session && o.session.date > today)
      .sort((a, b) => a.session!.date.localeCompare(b.session!.date));
    return upcoming[0] ?? options[0] ?? null;
  }

  selectPeriod(periodId: string): void {
    this.selectedPeriodId.set(periodId);
    this.loadStatuses();
  }

  loadStatuses(): void {
    const periodId = this.selectedPeriodId();
    if (!periodId) {
      return;
    }
    this.statusesLoading.set(true);
    this.statusesError.set(false);
    this.contributionService.listParticipantStatuses(this.id(), periodId).subscribe({
      next: (statuses) => {
        this.statuses.set(statuses);
        this.statusesLoading.set(false);
      },
      error: () => {
        this.statusesError.set(true);
        this.statusesLoading.set(false);
      },
    });
  }

  initials(name: string): string {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join('');
  }

  percent(row: ParticipantRow): number {
    return row.dueAmount ? Math.min(100, (row.validatedAmount / row.dueAmount) * 100) : 0;
  }

  contribute(): void {
    const definition = this.definition();
    const option = this.selectedPeriod();
    const mine = this.myRow();
    if (!definition || !option || !mine) {
      return;
    }
    const data: DeclarePaymentData = { definition, period: option.period, remaining: mine.remainingAmount };
    this.dialog
      .open(DeclarePaymentDialog, { data, width: '600px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((transaction?: ContributionTransaction) => {
        if (!transaction) {
          return;
        }
        this.loadStatuses();
        const upload: ProofUploadData = { transaction, justDeclared: true };
        this.dialog
          .open(ProofUploadDialog, { data: upload, width: '520px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
          .afterClosed()
          .subscribe((proof) =>
            this.snackBar.open(
              proof ? 'Justificatif envoyé' : 'Paiement déclaré, en attente de validation',
              'OK',
              { duration: 4000 },
            ),
          );
      });
  }
}
