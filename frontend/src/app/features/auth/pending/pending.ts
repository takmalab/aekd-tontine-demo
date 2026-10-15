import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';
import { OtpInput } from '../../../shared/components/otp-input/otp-input';

/**
 * Compte inscrit, pas encore validé (docs/decisions.md n° 54) : on y arrive après
 * l'inscription, ou en se connectant tant que l'administrateur n'a pas validé.
 * Le membre y confirme aussi son adresse e-mail avec le code reçu (n° 55).
 * `history.state` : `name` (prénom affiché), `email`, `codeSent` (inscription).
 */
@Component({
  selector: 'app-pending',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
    OtpInput,
  ],
  templateUrl: './pending.html',
  styleUrls: ['../register/register.scss', './pending.scss'],
})
export class Pending {
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  private readonly state = (history.state ?? {}) as { name?: string; email?: string; codeSent?: boolean };
  readonly name: string | null = this.state.name?.split(' ')[0] ?? null;

  /** Étape de confirmation de l'adresse : saisie de l'adresse, saisie du code, ou terminée. */
  readonly step = signal<'email' | 'code' | 'done'>(this.state.email && this.state.codeSent ? 'code' : 'email');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly resendIn = signal(0);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly emailForm = this.fb.group({
    email: [this.state.email ?? '', [Validators.required, Validators.email]],
  });
  readonly codeForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopTimer());
    if (this.step() === 'code') {
      // Le code vient d'être envoyé à l'inscription : même délai avant d'en redemander un.
      this.startTimer(60);
    }
  }

  get email(): string {
    return this.emailForm.controls.email.value!.trim();
  }

  sendCode(): void {
    if (this.emailForm.invalid) {
      this.emailForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.sendEmailVerification(this.email).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.step.set('code');
        this.codeForm.reset();
        this.startTimer(response.resendAfterSeconds);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(error.error?.message || "L'envoi du code a échoué. Réessayez dans un instant.");
      },
    });
  }

  verify(): void {
    if (this.loading()) {
      return;
    }
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.verifyEmailAddress(this.email, this.codeForm.getRawValue().code!).subscribe({
      next: () => {
        this.loading.set(false);
        this.stopTimer();
        this.step.set('done');
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(error.error?.message || 'Code invalide ou expiré.');
        this.codeForm.reset();
      },
    });
  }

  changeEmail(): void {
    this.stopTimer();
    this.errorMessage.set(null);
    this.step.set('email');
  }

  private startTimer(seconds: number): void {
    this.stopTimer();
    this.resendIn.set(seconds);
    this.timer = setInterval(() => {
      this.resendIn.update((s) => Math.max(0, s - 1));
      if (this.resendIn() === 0) {
        this.stopTimer();
      }
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
