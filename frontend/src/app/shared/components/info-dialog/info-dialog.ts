import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

export interface InfoDialogData {
  title: string;
  message: string;
  icon?: string;
  /** Lien optionnel affiché comme action secondaire (ex. rediriger vers un écran existant). */
  actionLabel?: string;
  actionRoute?: string[];
}

/**
 * Dialogue d'information générique — utilisé pour les actions dont
 * l'implémentation complète n'existe pas encore (« à venir »), plutôt que
 * de faire semblant qu'un bouton fonctionne (CLAUDE.md §37 : ne pas
 * inventer de fonctionnalité, signaler clairement ce qui manque).
 */
@Component({
  selector: 'app-info-dialog',
  imports: [RouterLink, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="confirm">
      <span class="confirm__icon"><mat-icon>{{ data.icon ?? 'info' }}</mat-icon></span>
      <h2 mat-dialog-title class="confirm__title">{{ data.title }}</h2>
      <mat-dialog-content class="confirm__message">{{ data.message }}</mat-dialog-content>
      <mat-dialog-actions align="end" class="confirm__actions">
        @if (data.actionLabel && data.actionRoute) {
          <a mat-button [routerLink]="data.actionRoute" mat-dialog-close>{{ data.actionLabel }}</a>
        }
        <button mat-flat-button type="button" mat-dialog-close cdkFocusInitial>Compris</button>
      </mat-dialog-actions>
    </div>
  `,
  styles: `
    .confirm {
      padding: 22px 8px 8px;
    }
    .confirm__icon {
      display: grid;
      place-items: center;
      width: 48px;
      height: 48px;
      margin: 0 24px 8px;
      border-radius: 14px;
      background: var(--aekd-orange-soft);
      color: var(--aekd-orange);
    }
    .confirm__title {
      font-size: 19px !important;
      font-weight: 700 !important;
      padding-top: 4px !important;
      color: var(--aekd-gray);
    }
    .confirm__title::before {
      display: none !important;
    }
    .confirm__message {
      font-size: 14px;
      line-height: 1.5;
      color: var(--aekd-gray-muted) !important;
    }
    .confirm__actions {
      gap: 8px;
      padding: 8px 24px 16px !important;
    }
    .confirm__actions button[mat-flat-button] {
      border-radius: 10px;
    }
  `,
})
export class InfoDialog {
  readonly data = inject<InfoDialogData>(MAT_DIALOG_DATA);
}
