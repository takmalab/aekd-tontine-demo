import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { PageState } from '../../../shared/components/page-state/page-state';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import {
  CONTRIBUTION_STATUS_LABELS,
  FREQUENCY_LABELS,
  FUND_DESTINATION_LABELS,
} from '../../contributions/contribution.model';
import { SESSION_TIMING_LABELS, Session, SessionContribution, SessionTiming } from '../session.model';
import { SessionService } from '../session.service';

@Component({
  selector: 'app-session-detail',
  imports: [RouterLink, DatePipe, MatButtonModule, MatIconModule, PageState],
  templateUrl: './session-detail.html',
  styleUrl: './session-detail.scss',
})
export class SessionDetail implements OnInit {
  private readonly sessionService = inject(SessionService);
  private readonly authService = inject(AuthService);

  /** Paramètre de route `:id` (withComponentInputBinding). */
  readonly id = input.required<string>();

  readonly isStaff = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);
  readonly timingLabels = SESSION_TIMING_LABELS;
  readonly destinationLabels = FUND_DESTINATION_LABELS;
  readonly statusLabels = CONTRIBUTION_STATUS_LABELS;
  readonly frequencyLabels = FREQUENCY_LABELS;
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;

  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly error = signal(false);
  readonly session = signal<Session | null>(null);
  readonly contributions = signal<SessionContribution[]>([]);

  readonly timing = computed<SessionTiming | null>(() => {
    const session = this.session();
    return session ? this.sessionService.timingOf(session) : null;
  });

  readonly mandatory = computed(() => this.contributions().filter((c) => c.definition.mandatory));
  readonly optional = computed(() => this.contributions().filter((c) => !c.definition.mandatory));

  /** Obligatoires puis facultatives, chaque cotisation restant une carte distincte. */
  readonly groups = computed(() => [
    { title: 'Cotisations obligatoires', icon: 'task_alt', items: this.mandatory() },
    { title: 'Cotisations facultatives', icon: 'volunteer_activism', items: this.optional() },
  ]);

  /**
   * Total des cotisations obligatoires ACTIVES à montant fixe (CLAUDE.md §9 :
   * 50 000 + 10 000 + 2 000 = 62 000 FCFA, hors facultatives). Chaque cotisation
   * reste affichée séparément ; ce total n'est qu'un récapitulatif.
   */
  readonly mandatoryTotal = computed(() =>
    this.mandatory()
      .filter((c) => c.definition.status === 'ACTIVE' && c.definition.amountMode === 'FIXED')
      .reduce((sum, c) => sum + (c.definition.amount ?? 0), 0),
  );

  readonly draftCount = computed(
    () => this.contributions().filter((c) => c.definition.status !== 'ACTIVE').length,
  );

  initials(fullName: string): string {
    return fullName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join('');
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.notFound.set(false);

    forkJoin({
      session: this.sessionService.get(this.id()),
      contributions: this.sessionService.contributionsOf(this.id()),
    }).subscribe({
      next: ({ session, contributions }) => {
        this.session.set(session);
        this.contributions.set(
          // Montant décroissant (montants libres en dernier), puis nom.
          [...contributions].sort(
            (a, b) =>
              (b.definition.amount ?? -1) - (a.definition.amount ?? -1) ||
              a.definition.name.localeCompare(b.definition.name, 'fr'),
          ),
        );
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.notFound.set(err.status === 404 || err.status === 400);
        this.error.set(!(err.status === 404 || err.status === 400));
        this.loading.set(false);
      },
    });
  }
}
