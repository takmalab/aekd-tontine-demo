import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY, Observable, catchError, filter, map, switchMap, tap } from 'rxjs';
import {
  ConfirmDialog,
  ConfirmDialogData,
} from '../../shared/components/confirm-dialog/confirm-dialog';
import {
  ContributionEditDialog,
  ContributionEditDialogData,
} from './contribution-edit-dialog/contribution-edit-dialog';
import { ContributionDefinition } from './contribution.model';
import { ContributionService } from './contribution.service';

/**
 * Parcours de modification, désactivation et suppression d'une cotisation
 * (dialogue, confirmation, notification), partagés par la liste et le détail.
 * Chaque méthode n'émet qu'en cas de succès ; les erreurs sont affichées ici.
 */
@Injectable({ providedIn: 'root' })
export class ContributionActions {
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly contributionService = inject(ContributionService);

  edit(definition: ContributionDefinition): Observable<ContributionDefinition> {
    const data: ContributionEditDialogData = { definition };
    return this.dialog
      .open<ContributionEditDialog, ContributionEditDialogData, ContributionDefinition>(
        ContributionEditDialog,
        {
          data,
          width: '560px',
          maxWidth: 'calc(100vw - 32px)',
          autoFocus: 'first-tabbable',
        },
      )
      .afterClosed()
      .pipe(
        filter((updated): updated is ContributionDefinition => !!updated),
        tap((updated) =>
          this.snackBar.open(`Cotisation « ${updated.name} » modifiée`, 'OK', { duration: 4000 }),
        ),
      );
  }

  /** DRAFT ou INACTIVE -> ACTIVE. */
  activate(definition: ContributionDefinition): Observable<ContributionDefinition> {
    return this.confirm({
      title:
        definition.status === 'INACTIVE' ? 'Réactiver la cotisation ?' : 'Activer la cotisation ?',
      message: `« ${definition.name} » deviendra visible selon sa visibilité et ses participants pourront déclarer leurs paiements.`,
      confirmLabel: definition.status === 'INACTIVE' ? 'Réactiver' : 'Activer',
      icon: 'rocket_launch',
    }).pipe(
      switchMap(() =>
        this.handle(this.contributionService.activate(definition.id), "L'activation a échoué."),
      ),
      tap((updated) =>
        this.snackBar.open(`Cotisation « ${updated.name} » activée`, 'OK', { duration: 4000 }),
      ),
    );
  }

  deactivate(definition: ContributionDefinition): Observable<ContributionDefinition> {
    return this.confirm({
      title: 'Désactiver la cotisation ?',
      message: `« ${definition.name} » ne sera plus proposée aux membres. Les paiements déjà enregistrés sont conservés et la cotisation pourra être réactivée.`,
      confirmLabel: 'Désactiver',
      icon: 'pause_circle',
    }).pipe(
      switchMap(() =>
        this.handle(
          this.contributionService.deactivate(definition.id),
          'La désactivation a échoué.',
        ),
      ),
      tap((updated) =>
        this.snackBar.open(`Cotisation « ${updated.name} » désactivée`, 'OK', { duration: 4000 }),
      ),
    );
  }

  /** Émet l'identifiant supprimé. */
  delete(definition: ContributionDefinition): Observable<string> {
    return this.confirm({
      title: 'Supprimer la cotisation ?',
      message: `« ${definition.name} » sera définitivement supprimée, avec ses participants, bénéficiaires, rattachements aux séances et règles de sanction. Cette action est irréversible.`,
      confirmLabel: 'Supprimer',
      icon: 'delete',
    }).pipe(
      switchMap(() =>
        this.handle(this.contributionService.delete(definition.id), 'La suppression a échoué.'),
      ),
      map(() => definition.id),
      tap(() =>
        this.snackBar.open(`Cotisation « ${definition.name} » supprimée`, 'OK', { duration: 4000 }),
      ),
    );
  }

  private confirm(data: ConfirmDialogData): Observable<true> {
    return this.dialog
      .open(ConfirmDialog, { data, width: '440px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed()
      .pipe(filter((confirmed): confirmed is true => confirmed === true));
  }

  /** Affiche le message métier du backend (ex. 409 : des paiements existent) et termine sans émettre. */
  private handle<T>(request: Observable<T>, fallback: string): Observable<T> {
    return request.pipe(
      catchError((err: HttpErrorResponse) => {
        this.snackBar.open(
          err.status === 409 || err.status === 400 ? err.error?.message || fallback : fallback,
          'OK',
          { duration: 7000 },
        );
        return EMPTY;
      }),
    );
  }
}
