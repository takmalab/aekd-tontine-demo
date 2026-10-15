import { MemberPhotoService } from '../../../core/services/member-photo.service';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, forkJoin, of } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { DocumentService } from '../../../core/services/document.service';
import { ActionMenu, MenuAction } from '../../../shared/components/action-menu/action-menu';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { InfoDialog, InfoDialogData } from '../../../shared/components/info-dialog/info-dialog';
import { PageState } from '../../../shared/components/page-state/page-state';
import { SearchBar } from '../../../shared/components/search-bar/search-bar';
import { StatusBadge, StatusVariant } from '../../../shared/components/status-badge/status-badge';
import { exportToCsv } from '../../../shared/utils/csv-export';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import { pageCount, paginate } from '../../../shared/utils/pagination';
import { SessionSummary } from '../../dashboard/dashboard.model';
import { AppUser, UserStatus } from '../../users/user.model';
import { UserService } from '../../users/user.service';
import { MemberEditDialog, MemberEditDialogData } from '../member-edit-dialog/member-edit-dialog';
import { MemberStatCard } from '../member-stat-card/member-stat-card';
import { Member, MemberSummary } from '../member.model';
import { MemberService } from '../member.service';

/**
 * Statut affiché pour un membre. `Member.active` (backend) ne distingue que
 * actif/inactif : le statut « En attente » vient du compte utilisateur lié
 * (`User.status === PENDING_VALIDATION`, visible uniquement pour l'ADMIN via
 * `/api/users`). Pour le TRESORIER (pas d'accès à `/api/users`), ou si un
 * membre n'a pas de compte correspondant, seul actif/suspendu est déductible.
 */
type MemberStatusUi = 'ACTIVE' | 'PENDING' | 'SUSPENDED';
type StatusFilterValue = MemberStatusUi | 'ALL';

/**
 * Situation du membre pour les cotisations obligatoires de la séance actuelle.
 * LATE l'emporte sur PENDING, qui l'emporte sur IN_PROGRESS (reste à payer).
 */
type ContributionState = 'NONE' | 'UP_TO_DATE' | 'IN_PROGRESS' | 'PENDING' | 'LATE';
type SituationFilterValue = 'ALL' | 'UP_TO_DATE' | 'LATE' | 'WITH_LOAN' | 'WITH_SANCTION';

const SITUATION_EXPORT_LABELS: Record<SituationFilterValue, string> = {
  ALL: 'Toutes',
  UP_TO_DATE: 'À jour',
  LATE: 'En retard',
  WITH_LOAN: 'Avec prêt en cours',
  WITH_SANCTION: 'Avec sanction active',
};
type SortValue = 'name' | 'recent' | 'savings' | 'loans';
type Tone = 'blue' | 'green' | 'orange' | 'red' | 'violet' | 'brown' | 'gray';
type RowAction = 'toggle-active' | 'export' | 'statement';

const STATUS_UI_LABELS: Record<MemberStatusUi, string> = {
  ACTIVE: 'Actif',
  PENDING: 'En attente',
  SUSPENDED: 'Suspendu',
};

const STATUS_UI_BADGE_VARIANT: Record<MemberStatusUi, StatusVariant> = {
  ACTIVE: 'success',
  PENDING: 'warning',
  SUSPENDED: 'danger',
};

const STATUS_UI_TONE: Record<MemberStatusUi, Tone> = {
  ACTIVE: 'green',
  PENDING: 'orange',
  SUSPENDED: 'red',
};

const CONTRIBUTION_STATE_LABELS: Record<ContributionState, string> = {
  NONE: 'Aucune',
  UP_TO_DATE: 'À jour',
  IN_PROGRESS: 'À payer',
  PENDING: 'En validation',
  LATE: 'En retard',
};

const CONTRIBUTION_STATE_TONE: Record<ContributionState, Tone> = {
  NONE: 'gray',
  UP_TO_DATE: 'green',
  IN_PROGRESS: 'blue',
  PENDING: 'orange',
  LATE: 'red',
};

const AVATAR_TONES: Tone[] = ['blue', 'violet', 'green', 'orange', 'brown'];
const PAGE_SIZE = 8;

interface MemberRow {
  member: Member;
  userStatus: UserStatus | null;
  statusUi: MemberStatusUi;
  initials: string;
  avatarTone: Tone;
  /** `null` si l'agrégat backend n'a pas pu être chargé. */
  summary: MemberSummary | null;
  contributionState: ContributionState;
}

interface DonutArc {
  tone: Tone;
  dashArray: string;
  dashOffset: number;
}

interface StackSegment {
  tone: Tone;
  label: string;
  count: number;
  percent: number;
}

function initialsOf(fullName: string): string {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

function hashCode(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = (hash << 5) - hash + value.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

function statusUiOf(member: Member, userStatus: UserStatus | null): MemberStatusUi {
  if (userStatus === 'PENDING_VALIDATION') {
    return 'PENDING';
  }
  if (userStatus === 'DISABLED') {
    return 'SUSPENDED';
  }
  return member.active ? 'ACTIVE' : 'SUSPENDED';
}

function contributionStateOf(summary: MemberSummary | null): ContributionState {
  if (!summary || summary.mandatoryContributionsCount === 0) {
    return 'NONE';
  }
  if (summary.lateContributionsCount > 0) {
    return 'LATE';
  }
  if (summary.pendingContributionsCount > 0) {
    return 'PENDING';
  }
  return summary.paidContributionsCount >= summary.mandatoryContributionsCount ? 'UP_TO_DATE' : 'IN_PROGRESS';
}

/**
 * Page Membres (ADMIN/TRESORIER). Données réelles : profils (`/api/members`),
 * statut des comptes (`/api/users`, ADMIN) et situation de chaque membre
 * (`/api/dashboard/members` : cotisations obligatoires de la séance actuelle,
 * épargne validée, encours de prêts, sanctions appliquées).
 */
@Component({
  selector: 'app-members-list',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTooltipModule,
    PageState,
    SearchBar,
    StatusBadge,
    ActionMenu,
    ExportMenu,
    MemberStatCard,
  ],
  templateUrl: './members-list.html',
  styleUrl: './members-list.scss',
})
export class MembersList implements OnInit {
  /** Photos de profil des membres (visibles par tous). */
  readonly photos = inject(MemberPhotoService);
  private readonly memberService = inject(MemberService);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly documentService = inject(DocumentService);

  readonly isAdmin = this.authService.hasRole('ADMIN');
  readonly fromIsoDate = fromIsoDate;
  readonly formatFcfa = formatFcfa;
  readonly statusLabels = STATUS_UI_LABELS;
  readonly badgeVariant = STATUS_UI_BADGE_VARIANT;
  readonly contributionLabels = CONTRIBUTION_STATE_LABELS;
  readonly contributionTone = CONTRIBUTION_STATE_TONE;

  readonly loading = signal(true);
  readonly error = signal(false);
  /** Les profils sont chargés mais l'agrégat financier a échoué : la page reste utilisable. */
  readonly summariesUnavailable = signal(false);
  readonly rows = signal<MemberRow[]>([]);
  readonly currentSession = signal<SessionSummary | null>(null);

  readonly search = signal('');
  readonly statusFilter = signal<StatusFilterValue>('ALL');
  readonly situationFilter = signal<SituationFilterValue>('ALL');
  readonly sort = signal<SortValue>('name');
  readonly pageIndex = signal(0);
  readonly pageSize = PAGE_SIZE;

  readonly totalCount = computed(() => this.rows().length);
  readonly activeCount = computed(() => this.rows().filter((r) => r.statusUi === 'ACTIVE').length);
  readonly pendingCount = computed(() => this.rows().filter((r) => r.statusUi === 'PENDING').length);
  readonly suspendedCount = computed(() => this.rows().filter((r) => r.statusUi === 'SUSPENDED').length);

  readonly statusFilters = computed<{ value: StatusFilterValue; label: string; tone: Tone; count: number }[]>(() => [
    { value: 'ALL', label: 'Tous', tone: 'blue', count: this.totalCount() },
    { value: 'ACTIVE', label: 'Actifs', tone: 'green', count: this.activeCount() },
    { value: 'PENDING', label: 'En attente', tone: 'orange', count: this.pendingCount() },
    { value: 'SUSPENDED', label: 'Suspendus', tone: 'red', count: this.suspendedCount() },
  ]);

  readonly totalSavings = computed(() => this.sumOf((s) => s.individualSavings));
  readonly totalOutstanding = computed(() => this.sumOf((s) => s.outstandingLoanBalance));
  readonly membersWithLoan = computed(() => this.rows().filter((r) => (r.summary?.activeLoansCount ?? 0) > 0).length);
  readonly membersWithSanction = computed(
    () => this.rows().filter((r) => (r.summary?.activeSanctionsCount ?? 0) > 0).length,
  );
  readonly upToDateCount = computed(() => this.rows().filter((r) => r.contributionState === 'UP_TO_DATE').length);
  readonly lateCount = computed(() => this.rows().filter((r) => r.contributionState === 'LATE').length);
  /** Membres concernés par au moins une cotisation obligatoire de la séance actuelle. */
  readonly concernedCount = computed(() => this.rows().filter((r) => r.contributionState !== 'NONE').length);

  /** Répartition des cotisations obligatoires de la séance actuelle (toutes lignes confondues). */
  readonly contributionStack = computed<{ total: number; segments: StackSegment[] }>(() => {
    let paid = 0;
    let pending = 0;
    let late = 0;
    let total = 0;
    for (const r of this.rows()) {
      if (!r.summary) continue;
      paid += r.summary.paidContributionsCount;
      pending += r.summary.pendingContributionsCount;
      late += r.summary.lateContributionsCount;
      total += r.summary.mandatoryContributionsCount;
    }
    const remaining = Math.max(0, total - paid - pending - late);
    const pct = (n: number) => (total === 0 ? 0 : (n / total) * 100);
    return {
      total,
      segments: [
        { tone: 'green' as Tone, label: 'Payées', count: paid, percent: pct(paid) },
        { tone: 'orange' as Tone, label: 'En validation', count: pending, percent: pct(pending) },
        { tone: 'red' as Tone, label: 'En retard', count: late, percent: pct(late) },
        { tone: 'blue' as Tone, label: 'À payer', count: remaining, percent: pct(remaining) },
      ],
    };
  });

  /** Recherche et filtres actifs, rappelés sous le titre du PDF exporté. */
  readonly exportFilters = computed(() => {
    const parts: string[] = [];
    const term = this.search().trim();
    if (term) {
      parts.push(`Recherche : « ${term} »`);
    }
    if (this.statusFilter() !== 'ALL') {
      parts.push(`Statut : ${this.statusFilters().find((f) => f.value === this.statusFilter())?.label}`);
    }
    if (this.situationFilter() !== 'ALL') {
      parts.push(`Situation : ${SITUATION_EXPORT_LABELS[this.situationFilter()]}`);
    }
    return parts.join(' · ') || null;
  });

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    const situation = this.situationFilter();
    let list = this.rows();
    if (status !== 'ALL') {
      list = list.filter((r) => r.statusUi === status);
    }
    if (situation === 'UP_TO_DATE') {
      list = list.filter((r) => r.contributionState === 'UP_TO_DATE');
    } else if (situation === 'LATE') {
      list = list.filter((r) => r.contributionState === 'LATE');
    } else if (situation === 'WITH_LOAN') {
      list = list.filter((r) => (r.summary?.activeLoansCount ?? 0) > 0);
    } else if (situation === 'WITH_SANCTION') {
      list = list.filter((r) => (r.summary?.activeSanctionsCount ?? 0) > 0);
    }
    if (term) {
      list = list.filter((r) =>
        [r.member.fullName, r.member.phone ?? '', r.member.email].join(' ').toLowerCase().includes(term),
      );
    }
    return this.sorted(list);
  });

  readonly paged = computed(() => paginate(this.filtered(), this.pageIndex(), this.pageSize));
  readonly pageCountValue = computed(() => pageCount(this.filtered().length, this.pageSize));
  readonly pageNumbers = computed(() => Array.from({ length: this.pageCountValue() }, (_, i) => i));
  readonly hasActiveFilters = computed(
    () => this.statusFilter() !== 'ALL' || this.situationFilter() !== 'ALL' || this.search().trim() !== '',
  );
  readonly rangeLabel = computed(() => {
    const total = this.filtered().length;
    if (total === 0) {
      return 'Aucun résultat';
    }
    const start = this.pageIndex() * this.pageSize + 1;
    const end = Math.min(total, start + this.pageSize - 1);
    return `Affichage de ${start} à ${end} sur ${total} membre${total > 1 ? 's' : ''}`;
  });

  readonly recentMembers = computed(() =>
    [...this.rows()].sort((a, b) => b.member.joinDate.localeCompare(a.member.joinDate)).slice(0, 4),
  );

  readonly donutArcs = computed<DonutArc[]>(() => {
    const total = this.totalCount();
    const radius = 54;
    const circumference = 2 * Math.PI * radius;
    if (total === 0) {
      return [];
    }
    const segments: { count: number; tone: Tone }[] = [
      { count: this.activeCount(), tone: 'green' },
      { count: this.pendingCount(), tone: 'orange' },
      { count: this.suspendedCount(), tone: 'red' },
    ];
    let offset = 0;
    return segments
      .filter((s) => s.count > 0)
      .map((s) => {
        const arcLength = (s.count / total) * circumference;
        const arc: DonutArc = { tone: s.tone, dashArray: `${arcLength} ${circumference - arcLength}`, dashOffset: -offset };
        offset += arcLength;
        return arc;
      });
  });

  readonly exportRows = computed(() => this.filtered().map((r) => this.toExportRow(r)));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    forkJoin({
      members: this.memberService.list(),
      // /api/users est réservé à l'ADMIN — le trésorier reçoit une liste vide et un statut dérivé de `active` seul.
      users: this.isAdmin ? this.userService.list().pipe(catchError(() => of([] as AppUser[]))) : of([] as AppUser[]),
      overview: this.memberService.overview().pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ members, users, overview }) => {
        const statusByMemberId = new Map(users.filter((u) => u.memberId).map((u) => [u.memberId!, u.status]));
        const summaryByMemberId = new Map((overview?.members ?? []).map((s) => [s.memberId, s]));
        this.summariesUnavailable.set(overview === null);
        this.currentSession.set(overview?.currentSession ?? null);
        this.rows.set(
          members.map((member) => {
            const userStatus = statusByMemberId.get(member.id) ?? null;
            const summary = summaryByMemberId.get(member.id) ?? null;
            return {
              member,
              userStatus,
              statusUi: statusUiOf(member, userStatus),
              initials: initialsOf(member.fullName),
              avatarTone: AVATAR_TONES[hashCode(member.id) % AVATAR_TONES.length],
              summary,
              contributionState: contributionStateOf(summary),
            };
          }),
        );
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  private sumOf(pick: (s: MemberSummary) => number): number {
    return this.rows().reduce((sum, r) => sum + (r.summary ? pick(r.summary) : 0), 0);
  }

  private sorted(list: MemberRow[]): MemberRow[] {
    const copy = [...list];
    switch (this.sort()) {
      case 'recent':
        return copy.sort((a, b) => b.member.joinDate.localeCompare(a.member.joinDate));
      case 'savings':
        return copy.sort((a, b) => (b.summary?.individualSavings ?? 0) - (a.summary?.individualSavings ?? 0));
      case 'loans':
        return copy.sort(
          (a, b) => (b.summary?.outstandingLoanBalance ?? 0) - (a.summary?.outstandingLoanBalance ?? 0),
        );
      default:
        return copy.sort((a, b) => a.member.fullName.localeCompare(b.member.fullName, 'fr'));
    }
  }

  percentOf(count: number): string {
    const total = this.totalCount();
    return total === 0 ? '0 %' : `${Math.round((count / total) * 100)} %`;
  }

  ratioOf(count: number): number {
    const total = this.totalCount();
    return total === 0 ? 0 : (count / total) * 100;
  }

  statusTone(statusUi: MemberStatusUi): Tone {
    return STATUS_UI_TONE[statusUi];
  }

  contributionPercent(row: MemberRow): number {
    const s = row.summary;
    return !s || s.mandatoryContributionsCount === 0 ? 0 : (s.paidContributionsCount / s.mandatoryContributionsCount) * 100;
  }

  onSearch(term: string): void {
    this.search.set(term);
    this.pageIndex.set(0);
  }

  onStatusFilter(status: StatusFilterValue): void {
    this.statusFilter.set(status);
    this.pageIndex.set(0);
  }

  onSituationFilter(situation: SituationFilterValue): void {
    this.situationFilter.set(situation);
    this.pageIndex.set(0);
  }

  onSort(sort: SortValue): void {
    this.sort.set(sort);
    this.pageIndex.set(0);
  }

  /** Applique un filtre de situation depuis un panneau latéral et fait défiler jusqu'au tableau. */
  showSituation(situation: SituationFilterValue): void {
    this.statusFilter.set('ALL');
    this.onSituationFilter(situation);
    this.scrollToTable();
  }

  resetFilters(): void {
    this.statusFilter.set('ALL');
    this.situationFilter.set('ALL');
    this.search.set('');
    this.pageIndex.set(0);
  }

  goToTable(): void {
    this.resetFilters();
    this.sort.set('recent');
    this.scrollToTable();
  }

  private scrollToTable(): void {
    document.getElementById('members-table')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  view(row: MemberRow): void {
    const data: MemberEditDialogData = {
      member: row.member,
      readonly: true,
      summary: row.summary,
      sessionLabel: this.currentSession()?.label ?? null,
    };
    this.dialog.open(MemberEditDialog, { data, width: '560px', maxWidth: 'calc(100vw - 24px)', autoFocus: false });
  }

  edit(row: MemberRow): void {
    this.dialog
      .open(MemberEditDialog, { data: row.member, width: '520px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((updated?: Member) => {
        if (updated) {
          this.replaceMember(updated);
          this.snackBar.open('Membre mis à jour', 'OK', { duration: 4000 });
        }
      });
  }

  private replaceMember(updated: Member): void {
    this.rows.update((list) =>
      list.map((r) =>
        r.member.id === updated.id
          ? { ...r, member: updated, initials: initialsOf(updated.fullName), statusUi: statusUiOf(updated, r.userStatus) }
          : r,
      ),
    );
  }

  rowActions(row: MemberRow): MenuAction<RowAction>[] {
    const actions: MenuAction<RowAction>[] = [];
    if (this.isAdmin) {
      actions.push(
        row.member.active
          ? { label: 'Suspendre le membre', icon: 'block', action: 'toggle-active', danger: true }
          : { label: 'Réactiver le membre', icon: 'check_circle', action: 'toggle-active' },
      );
    }
    actions.push({ label: 'Relevé individuel (PDF)', icon: 'picture_as_pdf', action: 'statement' });
    actions.push({ label: 'Exporter la fiche (CSV)', icon: 'download', action: 'export' });
    return actions;
  }

  onRowAction(action: RowAction, row: MemberRow): void {
    if (action === 'statement') {
      this.documentService.memberStatement(row.member.id);
    } else if (action === 'export') {
      exportToCsv(`membre-${row.member.fullName.replace(/\s+/g, '-').toLowerCase()}`, [this.toExportRow(row)]);
    } else if (action === 'toggle-active') {
      this.toggleActive(row);
    }
  }

  private toggleActive(row: MemberRow): void {
    const activate = !row.member.active;
    const data: ConfirmDialogData = activate
      ? {
          title: 'Réactiver le membre',
          message: `${row.member.fullName} sera de nouveau considéré comme membre actif.`,
          confirmLabel: 'Réactiver',
          icon: 'check_circle',
        }
      : {
          title: 'Suspendre le membre',
          message: `${row.member.fullName} restera consultable mais ne sera plus considéré comme membre actif.`,
          confirmLabel: 'Suspendre',
          icon: 'block',
        };
    this.dialog
      .open(ConfirmDialog, { data, width: '420px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed?: boolean) => {
        if (!confirmed) return;
        this.memberService.update(row.member.id, { active: activate }).subscribe({
          next: (updated) => {
            this.replaceMember(updated);
            this.snackBar.open(activate ? 'Membre réactivé' : 'Membre suspendu', 'OK', { duration: 4000 });
          },
          error: () => this.snackBar.open("L'opération a échoué. Réessayez.", 'OK', { duration: 5000 }),
        });
      });
  }

  private toExportRow(r: MemberRow): Record<string, string | number | null> {
    const s = r.summary;
    return {
      Nom: r.member.fullName,
      Email: r.member.email,
      Téléphone: r.member.phone,
      Statut: this.statusLabels[r.statusUi],
      "Date d'adhésion": r.member.joinDate,
      'Cotisations obligatoires payées (séance actuelle)': s
        ? `${s.paidContributionsCount}/${s.mandatoryContributionsCount}`
        : null,
      'Situation cotisations': this.contributionLabels[r.contributionState],
      'Épargne (FCFA)': s?.individualSavings ?? null,
      'Prêts en cours': s?.activeLoansCount ?? null,
      'Encours prêts (FCFA)': s?.outstandingLoanBalance ?? null,
      'Sanctions appliquées': s?.activeSanctionsCount ?? null,
    };
  }

  createMember(): void {
    const data: InfoDialogData = {
      title: 'Nouveau membre',
      icon: 'person_add',
      message:
        'Un membre est toujours créé avec un compte utilisateur associé. Utilisez « Utilisateurs → Nouveau compte » pour créer le compte et le profil membre en une seule fois.',
      actionLabel: 'Aller à Utilisateurs',
      actionRoute: ['/users'],
    };
    this.dialog.open(InfoDialog, { data, width: '440px', maxWidth: 'calc(100vw - 32px)' });
  }

  navigate(path: string): void {
    this.router.navigate([path]);
  }

  exportList(): void {
    exportToCsv('membres', this.exportRows());
  }
}
