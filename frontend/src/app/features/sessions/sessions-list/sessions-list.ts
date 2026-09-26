import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { PageState } from '../../../shared/components/page-state/page-state';
import { SearchBar } from '../../../shared/components/search-bar/search-bar';
import { fromIsoDate } from '../../../shared/utils/format';
import { SessionFormDialog } from '../session-form-dialog/session-form-dialog';
import { SESSION_TIMING_LABELS, Session, SessionTiming } from '../session.model';
import { SessionService } from '../session.service';

interface SessionRow {
  session: Session;
  date: Date;
  timing: SessionTiming;
}

type SortDirection = 'desc' | 'asc';

@Component({
  selector: 'app-sessions-list',
  imports: [RouterLink, DatePipe, MatButtonModule, MatFormFieldModule, MatSelectModule, MatIconModule, PageState, SearchBar],
  templateUrl: './sessions-list.html',
  styleUrl: './sessions-list.scss',
})
export class SessionsList implements OnInit {
  private readonly sessionService = inject(SessionService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  readonly timingLabels = SESSION_TIMING_LABELS;
  readonly canManage = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly sessions = signal<Session[]>([]);

  readonly search = signal('');
  readonly direction = signal<SortDirection>('desc');

  readonly allRows = computed<SessionRow[]>(() =>
    this.sessions().map((session) => ({
      session,
      date: fromIsoDate(session.date),
      timing: this.sessionService.timingOf(session),
    })),
  );

  readonly rows = computed<SessionRow[]>(() => {
    const term = this.search().trim().toLowerCase();
    const list = term
      ? this.allRows().filter((row) =>
          [row.session.label, row.session.location ?? ''].join(' ').toLowerCase().includes(term),
        )
      : this.allRows();

    // Le backend renvoie déjà du plus récent au plus ancien : on ne trie que si l'utilisateur inverse.
    return this.direction() === 'desc' ? list : [...list].reverse();
  });

  readonly currentCount = computed(() => this.allRows().filter((row) => row.timing === 'CURRENT').length);
  readonly upcomingCount = computed(() => this.allRows().filter((row) => row.timing === 'UPCOMING').length);

  onSearch(term: string): void {
    this.search.set(term);
  }

  toggleDirection(): void {
    this.direction.update((d) => (d === 'desc' ? 'asc' : 'desc'));
  }

  beneficiaryNames(session: Session): string {
    return session.beneficiaries.map((b) => b.memberFullName).join(', ');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.sessionService.list().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  openCreateDialog(): void {
    this.dialog
      .open<SessionFormDialog, void, Session>(SessionFormDialog, {
        width: '520px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((created) => {
        if (!created) {
          return;
        }
        this.snackBar
          .open(`Séance « ${created.label} » créée`, 'Ouvrir', { duration: 5000 })
          .onAction()
          .subscribe(() => this.router.navigate(['/sessions', created.id]));
        this.load();
      });
  }
}
