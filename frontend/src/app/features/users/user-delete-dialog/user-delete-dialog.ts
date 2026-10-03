import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AppUser, UserDeletionImpact } from '../user.model';
import { UserService } from '../user.service';

/**
 * Suppression définitive d'un compte (docs/decisions.md n° 41) : affiche ce qui
 * sera effacé, puis exige de retaper l'adresse e-mail du compte (action
 * irréversible). Se ferme avec `true` si le compte a été supprimé.
 */
@Component({
  selector: 'app-user-delete-dialog',
  imports: [
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="head">
      <span class="head__icon head__icon--danger"><mat-icon>delete_forever</mat-icon></span>
      <div class="head__text">
        <h2 mat-dialog-title class="head__title">Supprimer ce compte ?</h2>
        <p class="head__subtitle">{{ user.email }}{{ user.memberFullName ? ' · ' + user.memberFullName : '' }}</p>
      </div>
    </div>
    <mat-dialog-content class="body">
      @if (loading()) {
        <div class="center"><mat-spinner diameter="32" /></div>
      } @else if (impact(); as i) {
        <p class="danger">
          <mat-icon>warning</mat-icon>
          <span>
            Suppression <strong>définitive et irréversible</strong> du compte, de son profil membre et de
            <strong>tout son historique</strong>. Les fonds, l'épargne et les totaux affichés seront recalculés sans ces
            opérations.
          </span>
        </p>
        <ul class="impact">
          <li><span>Paiements de cotisations</span><strong>{{ i.contributionPayments }}</strong></li>
          <li><span>Participations aux cotisations</span><strong>{{ i.participations }}</strong></li>
          <li><span>Prêts</span><strong>{{ i.loans }}</strong></li>
          <li><span>Remboursements de prêts</span><strong>{{ i.loanRepayments }}</strong></li>
          <li><span>Versements d'épargne</span><strong>{{ i.savingsDeposits }}</strong></li>
          <li><span>Sanctions</span><strong>{{ i.appliedSanctions }}</strong></li>
        </ul>
        @if (i.actionsAsAuthor > 0) {
          <p class="note">
            <mat-icon>info</mat-icon>
            <span>
              Ce compte est l'auteur de {{ i.actionsAsAuthor }} opération(s) sur les données d'autres membres
              (validations, enregistrements…). Ces opérations sont <strong>conservées</strong> ; leur auteur
              apparaîtra comme « utilisateur supprimé ».
            </span>
          </p>
        }
        <mat-form-field appearance="outline" class="full">
          <mat-label>Pour confirmer, tapez l'adresse e-mail du compte</mat-label>
          <input matInput [ngModel]="typed()" (ngModelChange)="typed.set($event)" [placeholder]="user.email" autocomplete="off" />
        </mat-form-field>
      }
      @if (errorMessage(); as message) {
        <p class="notice" role="alert"><mat-icon>error_outline</mat-icon><span>{{ message }}</span></p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end" class="actions">
      <button mat-button type="button" mat-dialog-close [disabled]="deleting()">Annuler</button>
      <button mat-flat-button type="button" class="delete" [disabled]="!confirmed() || deleting()" (click)="confirm()">
        @if (deleting()) {
          <mat-spinner diameter="20" />
        } @else {
          Supprimer définitivement
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    @use '../../../shared/styles/dialog' as dlg;
    @include dlg.dialog-base;

    .head__icon--danger {
      background: var(--aekd-red-soft);
      color: var(--aekd-red-dark);
    }
    .center {
      display: grid;
      place-items: center;
      padding: 32px;
    }
    .danger,
    .note {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      margin: 0 0 14px;
      padding: 10px 12px;
      border-radius: 10px;
      font-size: 13px;
      line-height: 1.45;

      mat-icon {
        flex: none;
      }
    }
    .danger {
      background: var(--aekd-red-soft);
      color: var(--aekd-red-dark);
    }
    .note {
      background: var(--aekd-blue-soft);
      color: var(--aekd-gray);

      mat-icon {
        color: var(--aekd-blue);
      }
    }
    .impact {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 6px 16px;
      margin: 0 0 16px;
      padding: 0;
      list-style: none;
      font-size: 13px;

      li {
        display: flex;
        justify-content: space-between;
        gap: 8px;
        padding: 6px 10px;
        border-radius: 8px;
        background: #faf8f6;
      }
    }
    .delete:not([disabled]) {
      background: var(--aekd-red);
      color: #fff;
    }
    @media (max-width: 599.98px) {
      .impact {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class UserDeleteDialog implements OnInit {
  private readonly userService = inject(UserService);
  private readonly dialogRef = inject<MatDialogRef<UserDeleteDialog, boolean>>(MatDialogRef);
  readonly user = inject<AppUser>(MAT_DIALOG_DATA);

  readonly loading = signal(true);
  readonly deleting = signal(false);
  readonly impact = signal<UserDeletionImpact | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly typed = signal('');
  readonly confirmed = computed(
    () => !!this.impact() && this.typed().trim().toLowerCase() === this.user.email.toLowerCase(),
  );

  ngOnInit(): void {
    this.userService.deletionImpact(this.user.id).subscribe({
      next: (impact) => {
        this.impact.set(impact);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Impossible de calculer ce que la suppression effacerait.');
      },
    });
  }

  confirm(): void {
    this.deleting.set(true);
    this.errorMessage.set(null);
    this.userService.delete(this.user.id).subscribe({
      next: () => this.dialogRef.close(true),
      error: (error: HttpErrorResponse) => {
        this.deleting.set(false);
        this.errorMessage.set(
          error.status === 409
            ? error.error?.message || 'Suppression refusée.'
            : 'La suppression a échoué. Vérifiez que le serveur est démarré.',
        );
      },
    });
  }
}
