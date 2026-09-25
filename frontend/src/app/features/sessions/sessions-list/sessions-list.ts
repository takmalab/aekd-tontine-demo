import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { fromIsoDate } from '../../../shared/utils/format';
import { SessionFormDialog } from '../session-form-dialog/session-form-dialog';
import { SESSION_TIMING_LABELS, Session, SessionTiming } from '../session.model';
import { SessionService } from '../session.service';

interface SessionRow {
  session: Session;
  date: Date;
  timing: SessionTiming;
}

@Component({
  selector: 'app-sessions-list',
  imports: [RouterLink, DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
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

  readonly rows = computed<SessionRow[]>(() =>
    this.sessions().map((session) => ({
      session,
      date: fromIsoDate(session.date),
      timing: this.sessionService.timingOf(session),
    })),
  );

  readonly currentCount = computed(() => this.rows().filter((row) => row.timing === 'CURRENT').length);
  readonly upcomingCount = computed(() => this.rows().filter((row) => row.timing === 'UPCOMING').length);

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
