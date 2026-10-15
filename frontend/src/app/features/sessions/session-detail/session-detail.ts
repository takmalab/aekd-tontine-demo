import { MemberPhotoService } from '../../../core/services/member-photo.service';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter, forkJoin, switchMap } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { DocumentService } from '../../../core/services/document.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { PageState } from '../../../shared/components/page-state/page-state';
import { formatFcfa, fromIsoDate } from '../../../shared/utils/format';
import {
  CONTRIBUTION_STATUS_LABELS,
  FREQUENCY_LABELS,
  FUND_DESTINATION_ICONS,
  FUND_DESTINATION_LABELS,
  PAYMENT_OPERATOR_LABELS,
} from '../../contributions/contribution.model';
import { PayoutDialog, PayoutDialogData } from '../payout-dialog/payout-dialog';
import {
  PayoutBeneficiaryShare,
  PayoutOverview,
  SESSION_TIMING_LABELS,
  Session,
  SessionContribution,
  SessionPayout,
  SessionTiming,
} from '../session.model';
import { SessionFormDialog, SessionFormDialogData } from '../session-form-dialog/session-form-dialog';
import { SessionService } from '../session.service';

@Component({
  selector: 'app-session-detail',
  imports: [RouterLink, DatePipe, MatButtonModule, MatIconModule, MatTooltipModule, PageState],
  templateUrl: './session-detail.html',
  styleUrl: './session-detail.scss',
})
export class SessionDetail implements OnInit {
  /** Photos de profil des membres (visibles par tous). */
  readonly photos = inject(MemberPhotoService);
  private readonly sessionService = inject(SessionService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  readonly documents = inject(DocumentService);

  /** Paramètre de route `:id` (withComponentInputBinding). */
  readonly id = input.required<string>();

  readonly isStaff = this.authService.hasAnyRole(['ADMIN', 'TRESORIER']);
  readonly timingLabels = SESSION_TIMING_LABELS;
  readonly destinationLabels = FUND_DESTINATION_LABELS;
  readonly destinationIcons = FUND_DESTINATION_ICONS;
  readonly statusLabels = CONTRIBUTION_STATUS_LABELS;
  readonly frequencyLabels = FREQUENCY_LABELS;
  readonly formatFcfa = formatFcfa;
  readonly fromIsoDate = fromIsoDate;

  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly error = signal(false);
  readonly session = signal<Session | null>(null);
  readonly contributions = signal<SessionContribution[]>([]);
  /** Cagnotte remise aux bénéficiaires (personnel uniquement, décision n° 46). */
  readonly payout = signal<PayoutOverview | null>(null);
  readonly payoutError = signal(false);
  readonly operatorLabels = PAYMENT_OPERATOR_LABELS;

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

  /**
   * Suppression proposée seulement si aucune cotisation de la séance n'a de
   * paiement (le backend refuse aussi en présence de sanctions appliquées).
   */
  readonly canDelete = computed(
    () =>
      this.contributions().every((c) => !c.definition.hasPayments) && (this.payout()?.payouts.length ?? 0) === 0,
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
        this.loadPayout();
      },
      error: (err: HttpErrorResponse) => {
        this.notFound.set(err.status === 404 || err.status === 400);
        this.error.set(!(err.status === 404 || err.status === 400));
        this.loading.set(false);
      },
    });
  }

  loadPayout(): void {
    if (!this.isStaff) {
      return;
    }
    this.payoutError.set(false);
    this.sessionService.payoutOverview(this.id()).subscribe({
      next: (overview) => this.payout.set(overview),
      error: () => this.payoutError.set(true),
    });
  }

  openPayout(overview: PayoutOverview, share: PayoutBeneficiaryShare): void {
    const data: PayoutDialogData = { sessionId: overview.sessionId, sessionLabel: overview.sessionLabel, share };
    this.dialog
      .open<PayoutDialog, PayoutDialogData, SessionPayout>(PayoutDialog, {
        data,
        width: '560px',
        maxWidth: 'calc(100vw - 32px)',
      })
      .afterClosed()
      .subscribe((payout) => {
        if (!payout) {
          return;
        }
        this.loadPayout();
        this.snackBar.open(
          `Remise de ${formatFcfa(payout.amount)} à ${payout.memberFullName} enregistrée`,
          'OK',
          { duration: 4000 },
        );
      });
  }

  edit(session: Session): void {
    const data: SessionFormDialogData = { session };
    this.dialog
      .open<SessionFormDialog, SessionFormDialogData, Session>(SessionFormDialog, {
        data,
        width: '600px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((updated) => {
        if (!updated) {
          return;
        }
        // Rechargement complet : les cotisations rattachées ont pu changer.
        this.load();
        this.snackBar.open(`Séance « ${updated.label} » modifiée`, 'OK', { duration: 4000 });
      });
  }

  remove(session: Session): void {
    const count = this.contributions().length;
    const data: ConfirmDialogData = {
      title: 'Supprimer la séance ?',
      message:
        `« ${session.label} » sera définitivement supprimée` +
        (count > 0 ? `, ainsi que son rattachement à ${count} cotisation${count > 1 ? 's' : ''}` : '') +
        '. Les cotisations elles-mêmes sont conservées. Cette action est irréversible.',
      confirmLabel: 'Supprimer',
      icon: 'delete',
    };
    this.dialog
      .open(ConfirmDialog, { data, width: '440px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.sessionService.delete(session.id)),
      )
      .subscribe({
        next: () => {
          this.snackBar.open(`Séance « ${session.label} » supprimée`, 'OK', { duration: 4000 });
          this.router.navigate(['/sessions']);
        },
        error: (err: HttpErrorResponse) =>
          this.snackBar.open(
            err.status === 409 ? err.error?.message || 'Suppression impossible.' : 'La suppression a échoué.',
            'OK',
            { duration: 7000 },
          ),
      });
  }
}
