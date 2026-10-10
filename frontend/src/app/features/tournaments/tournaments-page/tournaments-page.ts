import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EMPTY, Observable, catchError, filter, finalize, switchMap } from 'rxjs';
import { ActionMenu, MenuAction } from '../../../shared/components/action-menu/action-menu';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog';
import { TournamentFormDialog, TournamentFormDialogData } from '../tournament-form-dialog/tournament-form-dialog';
import { TOURNAMENT_STATUS_LABELS, Tournament, TournamentSlot, ruleLabel } from '../tournament.model';
import { TournamentService } from '../tournament.service';

type TournamentAction = 'edit' | 'stop' | 'delete';

/** État d'un passage, pour l'affichage de l'ordre. */
type SlotState = 'CREATED' | 'DELETED' | 'NEXT' | 'PLANNED' | 'CANCELLED';

const SLOT_STATE_LABELS: Record<SlotState, string> = {
  CREATED: 'Séance créée',
  DELETED: 'Séance supprimée',
  NEXT: 'Prochaine',
  PLANNED: 'Prévue',
  CANCELLED: 'Non générée',
};

/**
 * Tournois (docs/decisions.md n° 43) : chaque mois, une séance est créée
 * automatiquement chez le membre suivant de l'ordre de passage. ADMIN et TRESORIER.
 */
@Component({
  selector: 'app-tournaments-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    ActionMenu,
  ],
  templateUrl: './tournaments-page.html',
  styleUrl: './tournaments-page.scss',
})
export class TournamentsPage implements OnInit {
  private readonly tournamentService = inject(TournamentService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  readonly statusLabels = TOURNAMENT_STATUS_LABELS;
  readonly slotStateLabels = SLOT_STATE_LABELS;
  readonly ruleLabel = ruleLabel;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly tournaments = signal<Tournament[]>([]);
  readonly busyId = signal<string | null>(null);

  /** En cours d'abord, puis les plus récents. */
  readonly sorted = computed(() =>
    [...this.tournaments()].sort(
      (a, b) => Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE') || b.createdAt.localeCompare(a.createdAt),
    ),
  );

  /** Un seul tournoi en cours à la fois (règle backend). */
  readonly hasActive = computed(() => this.tournaments().some((t) => t.status === 'ACTIVE'));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.tournamentService.list().subscribe({
      next: (tournaments) => {
        this.tournaments.set(tournaments);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  generatedCount(t: Tournament): number {
    return t.slots.filter((s) => s.generated).length;
  }

  progress(t: Tournament): number {
    return t.slots.length === 0 ? 100 : (100 * this.generatedCount(t)) / t.slots.length;
  }

  slotState(t: Tournament, slot: TournamentSlot): SlotState {
    if (slot.generated) {
      return slot.sessionId ? 'CREATED' : 'DELETED';
    }
    if (t.status !== 'ACTIVE') {
      return 'CANCELLED';
    }
    const next = t.slots.find((s) => !s.generated);
    return next?.position === slot.position ? 'NEXT' : 'PLANNED';
  }

  /** Date à laquelle la prochaine séance sera créée (tournoi en cours). */
  nextCreation(t: Tournament): { slot: TournamentSlot; on: Date } | null {
    if (t.status !== 'ACTIVE') {
      return null;
    }
    const slot = t.slots.find((s) => !s.generated);
    if (!slot) {
      return null;
    }
    const [y, m, d] = slot.plannedDate.split('-').map(Number);
    return { slot, on: new Date(y, m - 1, d - t.leadDays) };
  }

  actionsFor(t: Tournament): MenuAction<TournamentAction>[] {
    const actions: MenuAction<TournamentAction>[] = [];
    if (t.status === 'ACTIVE') {
      actions.push({ label: 'Modifier', icon: 'edit', action: 'edit' });
      actions.push({ label: 'Arrêter', icon: 'stop_circle', action: 'stop', danger: true });
    }
    if (this.generatedCount(t) === 0) {
      actions.push({ label: 'Supprimer', icon: 'delete', action: 'delete', danger: true });
    }
    return actions;
  }

  onAction(action: TournamentAction, t: Tournament): void {
    switch (action) {
      case 'edit':
        this.openForm(t);
        break;
      case 'stop':
        this.stop(t);
        break;
      case 'delete':
        this.remove(t);
        break;
    }
  }

  openForm(tournament?: Tournament): void {
    const data: TournamentFormDialogData = { tournament };
    this.dialog
      .open<TournamentFormDialog, TournamentFormDialogData, Tournament>(TournamentFormDialog, {
        data,
        width: '680px',
        maxWidth: 'calc(100vw - 24px)',
        autoFocus: 'first-tabbable',
      })
      .afterClosed()
      .subscribe((saved) => {
        if (!saved) {
          return;
        }
        this.replace(saved);
        const created = this.generatedCount(saved);
        this.snackBar.open(
          tournament
            ? 'Tournoi mis à jour'
            : `Tournoi « ${saved.name} » créé` + (created > 0 ? ' : la première séance a été générée' : ''),
          'OK',
          { duration: 5000 },
        );
      });
  }

  private stop(t: Tournament): void {
    const data: ConfirmDialogData = {
      title: 'Arrêter ce tournoi ?',
      message:
        `« ${t.name} » ne créera plus de séances. Les ${this.generatedCount(t)} séance(s) déjà créées sont conservées. ` +
        'Cette action est définitive : il faudra créer un nouveau tournoi.',
      confirmLabel: 'Arrêter',
      icon: 'stop_circle',
    };
    this.confirm(data)
      .pipe(switchMap(() => this.run(t, this.tournamentService.stop(t.id))))
      .subscribe((updated) => {
        this.replace(updated);
        this.snackBar.open('Tournoi arrêté', 'OK', { duration: 4000 });
      });
  }

  private remove(t: Tournament): void {
    const data: ConfirmDialogData = {
      title: 'Supprimer ce tournoi ?',
      message: `« ${t.name} » sera définitivement supprimé. Aucune séance n'a encore été générée.`,
      confirmLabel: 'Supprimer',
      icon: 'delete',
    };
    this.confirm(data)
      .pipe(switchMap(() => this.run(t, this.tournamentService.delete(t.id))))
      .subscribe(() => {
        this.tournaments.update((list) => list.filter((x) => x.id !== t.id));
        this.snackBar.open('Tournoi supprimé', 'OK', { duration: 4000 });
      });
  }

  private confirm(data: ConfirmDialogData) {
    return this.dialog
      .open(ConfirmDialog, { data, width: '460px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .pipe(filter((confirmed) => confirmed === true));
  }

  /** Exécute une action en affichant l'indicateur ; une erreur est signalée puis l'action abandonnée. */
  private run<T>(t: Tournament, action$: Observable<T>): Observable<T> {
    this.busyId.set(t.id);
    return action$.pipe(
      catchError((error: HttpErrorResponse) => {
        this.snackBar.open(
          error.status === 409 ? (error.error?.message ?? 'Action impossible.') : "L'action a échoué.",
          'OK',
          { duration: 6000 },
        );
        return EMPTY;
      }),
      finalize(() => this.busyId.set(null)),
    );
  }

  private replace(saved: Tournament): void {
    this.tournaments.update((list) =>
      list.some((x) => x.id === saved.id) ? list.map((x) => (x.id === saved.id ? saved : x)) : [saved, ...list],
    );
  }
}
