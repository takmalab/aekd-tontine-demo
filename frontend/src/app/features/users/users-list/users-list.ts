import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { FilterChips, FilterOption } from '../../../shared/components/filter-chips/filter-chips';
import { AppPagination } from '../../../shared/components/pagination/pagination';
import { PageState } from '../../../shared/components/page-state/page-state';
import { SearchBar } from '../../../shared/components/search-bar/search-bar';
import { StatusBadge, StatusVariant } from '../../../shared/components/status-badge/status-badge';
import { paginate } from '../../../shared/utils/pagination';
import { UserCreateDialog } from '../user-create-dialog/user-create-dialog';
import { UserEditDialog } from '../user-edit-dialog/user-edit-dialog';
import { AppUser, ROLE_LABELS, USER_STATUS_LABELS, UserStatus } from '../user.model';
import { UserService } from '../user.service';

type StatusFilter = 'ALL' | UserStatus;
type SortKey = 'email' | 'createdAt';

const STATUS_BADGE_VARIANT: Record<UserStatus, StatusVariant> = {
  PENDING_VALIDATION: 'pending',
  ACTIVE: 'paid',
  DISABLED: 'not_paid',
};

/**
 * Gestion des comptes utilisateurs (CLAUDE.md §6, ADMIN uniquement) :
 * validation des auto-inscriptions, création directe, rôles et statut.
 * Recherche, filtre par statut, tri et pagination sont côté client (pas de
 * `Pageable` côté backend aujourd'hui).
 */
@Component({
  selector: 'app-users-list',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    PageState,
    SearchBar,
    StatusBadge,
    FilterChips,
    AppPagination,
    ExportMenu,
  ],
  templateUrl: './users-list.html',
  styleUrl: './users-list.scss',
})
export class UsersList implements OnInit {
  private readonly userService = inject(UserService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = USER_STATUS_LABELS;
  readonly badgeVariant = STATUS_BADGE_VARIANT;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly users = signal<AppUser[]>([]);
  readonly busyId = signal<string | null>(null);

  readonly search = signal('');
  readonly statusFilter = signal<StatusFilter>('ALL');
  readonly sortBy = signal<SortKey>('email');
  readonly pageIndex = signal<number>(0);
  readonly pageSize = signal<number>(25);

  readonly statusOptions = computed<FilterOption<StatusFilter>[]>(() => {
    const all = this.users();
    const count = (status: UserStatus) => all.filter((u) => u.status === status).length;
    return [
      { value: 'ALL', label: 'Tous', count: all.length },
      { value: 'PENDING_VALIDATION', label: 'En attente', count: count('PENDING_VALIDATION') },
      { value: 'ACTIVE', label: 'Actifs', count: count('ACTIVE') },
      { value: 'DISABLED', label: 'Désactivés', count: count('DISABLED') },
    ];
  });

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    const sortKey = this.sortBy();

    let list = this.users();
    if (status !== 'ALL') {
      list = list.filter((u) => u.status === status);
    }
    if (term) {
      list = list.filter((u) => [u.email, u.memberFullName ?? ''].join(' ').toLowerCase().includes(term));
    }

    return [...list].sort((a, b) => {
      // Les comptes en attente de validation restent prioritaires, quel que soit le tri choisi.
      const pendingDiff = Number(b.status === 'PENDING_VALIDATION') - Number(a.status === 'PENDING_VALIDATION');
      if (pendingDiff !== 0) {
        return pendingDiff;
      }
      return sortKey === 'email' ? a.email.localeCompare(b.email, 'fr') : b.createdAt.localeCompare(a.createdAt);
    });
  });

  readonly paged = computed(() => paginate(this.filtered(), this.pageIndex(), this.pageSize()));

  readonly exportRows = computed(() =>
    this.filtered().map((u) => ({
      Email: u.email,
      Membre: u.memberFullName,
      Statut: this.statusLabels[u.status],
      Rôles: u.roles.map((r) => this.roleLabels[r]).join(', '),
      'Créé le': u.createdAt,
    })),
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.userService.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  onSearch(term: string): void {
    this.search.set(term);
    this.pageIndex.set(0);
  }

  onStatusFilter(status: StatusFilter): void {
    this.statusFilter.set(status);
    this.pageIndex.set(0);
  }

  onSortChange(key: SortKey): void {
    this.sortBy.set(key);
    this.pageIndex.set(0);
  }

  create(): void {
    this.dialog
      .open(UserCreateDialog, { width: '560px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((user?: AppUser) => {
        if (user) {
          this.users.update((list) => [...list, user]);
          this.snackBar.open(`Compte « ${user.email} » créé et actif`, 'OK', { duration: 4000 });
        }
      });
  }

  edit(user: AppUser): void {
    this.dialog
      .open(UserEditDialog, { data: user, width: '480px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((updated?: AppUser) => {
        if (updated) {
          this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
          this.snackBar.open('Compte mis à jour', 'OK', { duration: 4000 });
        }
      });
  }

  approve(user: AppUser): void {
    const data: ConfirmDialogData = {
      title: 'Valider ce compte ?',
      message: `« ${user.email} » pourra se connecter avec le rôle Membre. Cette action est définitive.`,
      confirmLabel: 'Valider',
      icon: 'task_alt',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.busyId.set(user.id);
        this.userService.approve(user.id).subscribe({
          next: (updated) => {
            this.busyId.set(null);
            this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
            this.snackBar.open('Compte validé', 'OK', { duration: 4000 });
          },
          error: (error: HttpErrorResponse) => {
            this.busyId.set(null);
            this.snackBar.open(
              error.status === 404 ? 'Ce compte est introuvable.' : 'La validation a échoué.',
              'OK',
              { duration: 5000 },
            );
          },
        });
      });
  }
}
