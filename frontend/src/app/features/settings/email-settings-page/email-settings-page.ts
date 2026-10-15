import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';

/** Miroir de EmailSettingsController.SettingsResponse : la clé Brevo n'est jamais renvoyée. */
interface EmailSettings {
  configured: boolean;
  enabled: boolean;
  live: boolean;
  provider: string;
  fromAddress: string;
  fromName: string;
  updatedByEmail: string | null;
  updatedAt: string | null;
}

/**
 * « Paramètres e-mail » (super administrateur, docs/decisions.md n° 56) : état
 * de Brevo, envoi réel ou simulation, e-mail de test.
 */
@Component({
  selector: 'app-email-settings-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
  ],
  templateUrl: './email-settings-page.html',
  styleUrl: '../sms-settings-page/sms-settings-page.scss',
})
export class EmailSettingsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);
  private readonly url = `${environment.apiUrl}/settings/email`;

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly testing = signal(false);
  readonly settings = signal<EmailSettings | null>(null);
  readonly testResult = signal<{ ok: boolean; message: string } | null>(null);
  readonly testTo = new FormControl(inject(AuthService).currentUser()?.email ?? '', [
    Validators.required,
    Validators.email,
  ]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.http.get<EmailSettings>(this.url).subscribe({
      next: (settings) => {
        this.settings.set(settings);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  toggle(enabled: boolean): void {
    this.saving.set(true);
    this.http.put<EmailSettings>(this.url, { enabled }).subscribe({
      next: (settings) => {
        this.saving.set(false);
        this.settings.set(settings);
        this.snackBar.open(enabled ? 'Envoi réel des e-mails activé' : 'E-mails passés en simulation', 'OK', {
          duration: 4000,
        });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open("Le réglage n'a pas pu être enregistré.", 'OK', { duration: 6000 });
        this.load();
      },
    });
  }

  sendTest(): void {
    if (this.testTo.invalid) {
      this.testTo.markAsTouched();
      return;
    }
    this.testing.set(true);
    this.testResult.set(null);
    this.http.post<{ live: boolean; message: string }>(`${this.url}/test`, { to: this.testTo.value!.trim() }).subscribe({
      next: (result) => {
        this.testing.set(false);
        this.testResult.set({ ok: true, message: result.message });
      },
      error: (error: HttpErrorResponse) => {
        this.testing.set(false);
        this.testResult.set({ ok: false, message: error.error?.message || "L'envoi a échoué." });
      },
    });
  }
}
