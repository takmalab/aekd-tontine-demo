import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { AppPagination } from '../../../shared/components/pagination/pagination';
import { PageState } from '../../../shared/components/page-state/page-state';
import { SearchBar } from '../../../shared/components/search-bar/search-bar';
import { ExportMenu } from '../../../shared/components/export-menu/export-menu';
import { StatusBadge } from '../../../shared/components/status-badge/status-badge';
import { fromIsoDate } from '../../../shared/utils/format';
import { paginate } from '../../../shared/utils/pagination';
import { MemberEditDialog } from '../member-edit-dialog/member-edit-dialog';
import { Member } from '../member.model';
import { MemberService } from '../member.service';

type SortKey = 'name' | 'joinDate';

/**
 * Gestion des membres (CLAUDE.md §6) : consultable par ADMIN et TRESORIER ;
 * modification réservée à l'ADMIN (MemberController.update). Recherche, tri
 * et pagination sont côté client (le backend renvoie une simple liste, et
 * la tontine compte aujourd'hui moins de 30 membres — regles-metier.md §2).
 */
@Component({
  selector: 'app-members-list',
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    PageState,
    SearchBar,
    StatusBadge,
    AppPagination,
    ExportMenu,
  ],
  templateUrl: './members-list.html',
  styleUrl: './members-list.scss',
})
export class MembersList implements OnInit {
  private readonly memberService = inject(MemberService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly isAdmin = this.authService.hasRole('ADMIN');
  readonly fromIsoDate = fromIsoDate;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly members = signal<Member[]>([]);

  readonly search = signal('');
  readonly sortBy = signal<SortKey>('name');
  readonly pageIndex = signal<number>(0);
  readonly pageSize = signal<number>(25);

  readonly activeCount = computed(() => this.members().filter((m) => m.active).length);

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const list = term
      ? this.members().filter((m) =>
          [m.fullName, m.email, m.phone ?? ''].join(' ').toLowerCase().includes(term),
        )
      : this.members();

    const key = this.sortBy();
    return [...list].sort((a, b) =>
      key === 'name' ? a.fullName.localeCompare(b.fullName, 'fr') : b.joinDate.localeCompare(a.joinDate),
    );
  });

  readonly paged = computed(() => paginate(this.filtered(), this.pageIndex(), this.pageSize()));

  readonly exportRows = computed(() =>
    this.filtered().map((m) => ({
      Nom: m.fullName,
      Email: m.email,
      Téléphone: m.phone,
      "Date d'adhésion": m.joinDate,
      Statut: m.active ? 'Actif' : 'Inactif',
    })),
  );

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.memberService.list().subscribe({
      next: (members) => {
        this.members.set(members);
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

  onSortChange(key: SortKey): void {
    this.sortBy.set(key);
    this.pageIndex.set(0);
  }

  edit(member: Member): void {
    this.dialog
      .open(MemberEditDialog, { data: member, width: '520px', maxWidth: 'calc(100vw - 24px)', autoFocus: false })
      .afterClosed()
      .subscribe((updated?: Member) => {
        if (updated) {
          this.members.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
          this.snackBar.open('Membre mis à jour', 'OK', { duration: 4000 });
        }
      });
  }
}
