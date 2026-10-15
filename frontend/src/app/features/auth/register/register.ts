import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const { fullName, email, phone, password } = this.form.getRawValue();
    this.authService
      .register({ fullName: fullName!, email: email!, phone: phone || undefined, password: password! })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.form.reset();
          // Compte en attente de validation : page d'attente (decisions.md n° 54).
          this.router.navigate(['/pending'], { state: { name: fullName, email: email!.trim(), codeSent: true } });
        },
        error: (error: HttpErrorResponse) => {
          this.loading.set(false);
          const message = error.error?.message;
          this.errorMessage.set(
            (error.status === 400 || error.status === 409) && message
              ? message
              : error.status === 409
                ? 'Un compte existe déjà avec cet email.'
                : "Une erreur est survenue. Vérifiez que le serveur est démarré.",
          );
        },
      });
  }
}
