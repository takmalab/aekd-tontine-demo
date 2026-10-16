import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

export interface WhatsappShareData {
  title: string;
  /** Adresse de l'API qui renvoie { text } : le message rédigé par le serveur. */
  url?: string;
  /** Ou le message déjà rédigé par l'écran (ex. liste filtrée des sanctions, n° 62). */
  text?: string;
}

/**
 * Partage sur WhatsApp (docs/decisions.md n° 59) : le message est rédigé par le
 * serveur, modifiable ici, puis WhatsApp s'ouvre avec ce texte ; on choisit le
 * groupe de la tontine. Aucun envoi automatique.
 */
@Component({
  selector: 'app-whatsapp-share-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="head">
      <span class="head__icon head__icon--whatsapp"><mat-icon>share</mat-icon></span>
      <div class="head__text">
        <h2 mat-dialog-title class="head__title">Partager sur WhatsApp</h2>
        <p class="head__subtitle">{{ data.title }}</p>
      </div>
    </div>
    <mat-dialog-content class="body">
      @if (loading()) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      } @else if (error()) {
        <p class="error">Le message n'a pas pu être préparé.</p>
      } @else {
        <p class="hint">Relisez ou modifiez le message, puis choisissez le groupe de la tontine dans WhatsApp.</p>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Message</mat-label>
          <textarea matInput rows="14" [formControl]="text"></textarea>
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end" class="actions">
      <button mat-button type="button" mat-dialog-close>Fermer</button>
      <button mat-stroked-button type="button" (click)="copy()" [disabled]="loading() || error()">
        <mat-icon>content_copy</mat-icon> Copier
      </button>
      <button mat-flat-button type="button" class="whatsapp" (click)="open()" [disabled]="loading() || error()">
        <mat-icon>send</mat-icon> Ouvrir WhatsApp
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .head {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 20px 24px 0;
    }
    .head__icon {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      border-radius: 12px;
      background: #25d366;
      color: #fff;
    }
    .head__title {
      margin: 0;
      padding: 0;
      font-size: 18px;
    }
    .head__subtitle {
      margin: 2px 0 0;
      color: var(--aekd-gray-muted);
      font-size: 13px;
    }
    .body {
      padding-top: 16px;
    }
    .hint {
      margin: 0 0 10px;
      font-size: 13px;
      color: var(--aekd-gray-muted);
    }
    .full {
      width: 100%;
    }
    textarea {
      font-family: inherit;
      font-size: 13.5px;
      line-height: 1.45;
    }
    .loading {
      display: grid;
      place-items: center;
      padding: 32px;
    }
    .error {
      color: #c62828;
    }
    .actions {
      gap: 6px;
      padding: 8px 24px 20px;
    }
    .whatsapp {
      --mdc-filled-button-container-color: #25d366;
      --mat-button-filled-container-color: #1faa52;
    }
  `,
})
export class WhatsappShareDialog {
  private readonly snackBar = inject(MatSnackBar);
  readonly data = inject<WhatsappShareData>(MAT_DIALOG_DATA);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly text = new FormControl('', { nonNullable: true });

  constructor() {
    if (this.data.text !== undefined) {
      this.text.setValue(this.data.text);
      this.loading.set(false);
      return;
    }
    inject(HttpClient)
      .get<{ text: string }>(this.data.url!)
      .subscribe({
        next: (response) => {
          this.text.setValue(response.text);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }

  /** Ouvre WhatsApp (application sur téléphone, WhatsApp Web sur ordinateur) avec le message. */
  open(): void {
    window.open(`https://wa.me/?text=${encodeURIComponent(this.text.value)}`, '_blank', 'noopener');
  }

  copy(): void {
    navigator.clipboard
      .writeText(this.text.value)
      .then(() => this.snackBar.open('Message copié : collez-le dans le groupe WhatsApp', 'OK', { duration: 4000 }))
      .catch(() => this.snackBar.open('Copie impossible : sélectionnez le texte à la main', 'OK', { duration: 5000 }));
  }
}
