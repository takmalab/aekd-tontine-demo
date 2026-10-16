import { Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { OtpInput } from '../../../shared/components/otp-input/otp-input';
import { PhoneInput } from '../../../shared/components/phone-input/phone-input';
import { formatPhone } from '../../../shared/utils/countries';

type LoginMethod = 'email' | 'phone';
type OtpStep = 'phone' | 'code';
/** Canal du code : SMS (n° 47) ou e-mail pour une adresse vérifiée (n° 55). */
type OtpChannel = 'sms' | 'email';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    OtpInput,
    PhoneInput,
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
  /** Page demandée avant la connexion (lien reçu) ; seulement une adresse interne. */
  private readonly returnUrl = (() => {
    const url = new URLSearchParams(location.search).get('returnUrl');
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : '/dashboard';
  })();
  /** Mode maintenance (decisions.md n° 57) : message affiché en haut du formulaire. */
  readonly maintenanceMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);

  /** Connexion par email + mot de passe, ou par code SMS (docs/decisions.md n° 47). */
  readonly method = signal<LoginMethod>('phone');
  readonly otpStep = signal<OtpStep>('phone');
  readonly otpChannel = signal<OtpChannel>('sms');
  /** Adresse à laquelle le code e-mail a été demandé. */
  readonly otpEmail = signal<string | null>(null);
  /** Numéro normalisé renvoyé par le serveur (+237XXXXXXXXX). */
  readonly otpPhone = signal<string | null>(null);
  readonly expiresInMinutes = signal(5);
  /** Numéro affiché à l'étape du code, ex. « +237 6 00 00 00 03 ». */
  readonly displayedPhone = computed(() => formatPhone(this.otpPhone()));
  private readonly otpInput = viewChild(OtpInput);
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
    this.authService.publicInfo().subscribe({
      next: (info) => this.maintenanceMessage.set(info.maintenance ? info.maintenanceMessage : null),
      error: () => this.maintenanceMessage.set(null),
    });
  }

  selectMethod(method: LoginMethod): void {
    this.method.set(method);
    this.errorMessage.set(null);
    this.otpStep.set('phone');
    this.stopResendTimer();
  }

  /** Connexion par code e-mail (decisions.md n° 55) : pas besoin du mot de passe. */
  requestEmailCode(): void {
    const control = this.form.controls.email;
    const resending = this.otpStep() === 'code';
    if (!resending && control.invalid) {
      control.markAsTouched();
      return;
    }
    const email = resending ? this.otpEmail()! : control.value!.trim();
    this.loading.set(true);
    this.errorMessage.set(null);
    this.authService.requestEmailCode(email).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.otpChannel.set('email');
        this.otpEmail.set(response.email);
        this.expiresInMinutes.set(Math.round(response.expiresInSeconds / 60));
        this.otpStep.set('code');
        this.codeForm.reset();
        this.startResendTimer(response.resendAfterSeconds);
        setTimeout(() => this.otpInput()?.focusFirstEmpty());
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 403) {
          this.router.navigate(['/pending'], { state: { email } });
          return;
        }
        this.errorMessage.set(this.serverMessage(error));
      },
    });
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
        this.router.navigateByUrl(this.returnUrl);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        if (error.status === 403) {
          this.router.navigate(['/pending'], { state: { email: email!.trim() } });
          return;
        }
        this.errorMessage.set(
          error.status === 401
            ? 'Email ou mot de passe incorrect.'
            : "Une erreur est survenue. Vérifiez que le serveur est démarré.",
        );
      },
    });
  }

  requestCode(): void {
    if (this.otpStep() === 'code' && this.otpChannel() === 'email') {
      this.requestEmailCode();
      return;
    }
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
        this.otpChannel.set('sms');
        this.otpPhone.set(response.phone);
        this.expiresInMinutes.set(Math.round(response.expiresInSeconds / 60));
        this.otpStep.set('code');
        this.codeForm.reset();
        this.startResendTimer(response.resendAfterSeconds);
        setTimeout(() => this.otpInput()?.focusFirstEmpty());
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        // Numéro d'un compte pas encore validé (decisions.md n° 54).
        if (error.status === 403) {
          this.router.navigate(['/pending']);
          return;
        }
        this.errorMessage.set(this.serverMessage(error));
      },
    });
  }

  verifyCode(): void {
    // Appelé aussi automatiquement quand la 6e case est remplie.
    if (this.loading()) {
      return;
    }
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const code = this.codeForm.getRawValue().code!;
    const verification =
      this.otpChannel() === 'email'
        ? this.authService.verifyEmailCode(this.otpEmail()!, code)
        : this.authService.verifyOtp({ phone: this.otpPhone()!, code });
    verification.subscribe({
      next: () => {
        this.loading.set(false);
        this.stopResendTimer();
        this.router.navigateByUrl(this.returnUrl);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.serverMessage(error));
        this.codeForm.reset();
        setTimeout(() => this.otpInput()?.focusFirstEmpty());
      },
    });
  }

  phoneError(): string | null {
    const control = this.phoneForm.controls.phone;
    if (!control.touched || control.valid) {
      return null;
    }
    return control.hasError('required') ? 'Le numéro est requis' : 'Numéro invalide';
  }

  changePhone(): void {
    this.otpStep.set('phone');
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
