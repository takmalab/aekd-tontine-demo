import { Component, DestroyRef, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';

type LoginMethod = 'email' | 'phone';
type OtpStep = 'phone' | 'code';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);

  /** Connexion par email + mot de passe, ou par code SMS (docs/decisions.md n° 47). */
  readonly method = signal<LoginMethod>('email');
  readonly otpStep = signal<OtpStep>('phone');
  readonly otpInfo = signal<string | null>(null);
  /** Numéro normalisé renvoyé par le serveur (+237XXXXXXXXX). */
  readonly otpPhone = signal<string | null>(null);
  /** Secondes avant de pouvoir redemander un code. */
  readonly resendIn = signal(0);
  private resendTimer: ReturnType<typeof setInterval> | null = null;

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly phoneForm = this.fb.group({
    // Format camerounais vérifié par le serveur ; ici, seulement les caractères admis.
    phone: ['', [Validators.required, Validators.pattern(/^[\d\s+().-]{9,20}$/)]],
  });

  readonly codeForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopResendTimer());
  }

  selectMethod(method: LoginMethod): void {
    this.method.set(method);
    this.errorMessage.set(null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();
    this.authService.login({ email: email!, password: password! }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(
          error.status === 401
            ? 'Email ou mot de passe incorrect, ou compte pas encore validé par un administrateur.'
            : "Une erreur est survenue. Vérifiez que le serveur est démarré.",
        );
      },
    });
  }

  requestCode(): void {
    // "Renvoyer le code" depuis l'étape 2 réutilise le numéro déjà validé par le serveur.
    const resending = this.otpStep() === 'code';
    if (!resending && this.phoneForm.invalid) {
      this.phoneForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const phone = resending ? this.otpPhone()! : this.phoneForm.getRawValue().phone!;
    this.authService.requestOtp({ phone }).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.otpPhone.set(response.phone);
        this.otpInfo.set(response.message);
        this.otpStep.set('code');
        this.codeForm.reset();
        this.startResendTimer(response.resendAfterSeconds);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.serverMessage(error));
      },
    });
  }

  verifyCode(): void {
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.authService.verifyOtp({ phone: this.otpPhone()!, code: this.codeForm.getRawValue().code! }).subscribe({
      next: () => {
        this.loading.set(false);
        this.stopResendTimer();
        this.router.navigate(['/dashboard']);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.serverMessage(error));
      },
    });
  }

  changePhone(): void {
    this.otpStep.set('phone');
    this.otpInfo.set(null);
    this.errorMessage.set(null);
    this.stopResendTimer();
  }

  /** Les messages OTP du serveur sont rédigés pour l'utilisateur (essais restants, délai...). */
  private serverMessage(error: HttpErrorResponse): string {
    const message = error.error?.message;
    const userFacing = (error.status >= 400 && error.status < 500) || error.status === 503;
    if (userFacing && typeof message === 'string' && message) {
      return message;
    }
    return 'Une erreur est survenue. Vérifiez que le serveur est démarré.';
  }

  private startResendTimer(seconds: number): void {
    this.stopResendTimer();
    this.resendIn.set(seconds);
    this.resendTimer = setInterval(() => {
      const next = this.resendIn() - 1;
      this.resendIn.set(Math.max(next, 0));
      if (next <= 0) {
        this.stopResendTimer();
      }
    }, 1000);
  }

  private stopResendTimer(): void {
    if (this.resendTimer) {
      clearInterval(this.resendTimer);
      this.resendTimer = null;
    }
  }
}
