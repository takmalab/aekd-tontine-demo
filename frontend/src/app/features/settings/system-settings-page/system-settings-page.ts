import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { ThemeService } from '../../../core/services/theme.service';

/** Miroir de SystemSettingsService.SettingsResponse (docs/decisions.md n° 57). */
interface SystemSettingsResponse {
  settings: {
    associationName: string;
    associationShortName: string;
    associationAddress: string | null;
    associationPhone: string | null;
    associationEmail: string | null;
    customLogo: boolean;
    sessionDays: number | null;
    otpTtlMinutes: number | null;
    otpMaxAttempts: number | null;
    otpResendSeconds: number | null;
    otpMaxPerHour: number | null;
    maintenanceEnabled: boolean;
    maintenanceMessage: string | null;
  };
  effectiveSessionDays: number;
  effectiveOtpTtlMinutes: number;
  effectiveOtpMaxAttempts: number;
  effectiveOtpResendSeconds: number;
  effectiveOtpMaxPerHour: number;
  updatedByEmail: string | null;
  updatedAt: string | null;
}

/**
 * « Paramètres système » (super administrateur, decisions.md n° 57) :
 * association sur les PDF, durée de session, règles des codes, maintenance.
 * Une règle laissée vide reprend la valeur de la configuration du serveur.
 */
@Component({
  selector: 'app-system-settings-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    RouterLink,
  ],
  templateUrl: './system-settings-page.html',
  styleUrls: ['../sms-settings-page/sms-settings-page.scss', './system-settings-page.scss'],
})
export class SystemSettingsPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly themeService = inject(ThemeService);
  private readonly url = `${environment.apiUrl}/settings/system`;

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly data = signal<SystemSettingsResponse | null>(null);

  private readonly optionalInt = (min: number, max: number) => [Validators.min(min), Validators.max(max), Validators.pattern(/^\d*$/)];

  readonly form = this.fb.group({
    associationName: ['', [Validators.required, Validators.maxLength(150)]],
    associationShortName: ['', [Validators.required, Validators.maxLength(30)]],
    associationAddress: ['', Validators.maxLength(255)],
    associationPhone: ['', Validators.maxLength(30)],
    associationEmail: ['', [Validators.email, Validators.maxLength(255)]],
    sessionDays: [null as number | null, this.optionalInt(1, 365)],
    otpTtlMinutes: [null as number | null, this.optionalInt(1, 60)],
    otpMaxAttempts: [null as number | null, this.optionalInt(1, 20)],
    otpResendSeconds: [null as number | null, this.optionalInt(0, 3600)],
    otpMaxPerHour: [null as number | null, this.optionalInt(1, 100)],
    maintenanceEnabled: [false],
    maintenanceMessage: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.http.get<SystemSettingsResponse>(this.url).subscribe({
      next: (data) => {
        this.apply(data);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const num = (value: number | string | null) => (value === null || value === '' ? null : Number(value));
    this.saving.set(true);
    this.http
      .put<SystemSettingsResponse>(this.url, {
        associationName: v.associationName!.trim(),
        associationShortName: v.associationShortName!.trim(),
        associationAddress: v.associationAddress?.trim() || null,
        associationPhone: v.associationPhone?.trim() || null,
        associationEmail: v.associationEmail?.trim() || null,
        sessionDays: num(v.sessionDays),
        otpTtlMinutes: num(v.otpTtlMinutes),
        otpMaxAttempts: num(v.otpMaxAttempts),
        otpResendSeconds: num(v.otpResendSeconds),
        otpMaxPerHour: num(v.otpMaxPerHour),
        maintenanceEnabled: !!v.maintenanceEnabled,
        maintenanceMessage: v.maintenanceMessage?.trim() || null,
      })
      .subscribe({
        next: (data) => {
          this.saving.set(false);
          this.apply(data);
          // Nom et sigle affichés partout dans l'application (thème, décision n° 72).
          this.themeService.refresh();
          this.snackBar.open('Paramètres enregistrés', 'OK', { duration: 4000 });
        },
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          this.snackBar.open(error.error?.message || "Les paramètres n'ont pas pu être enregistrés.", 'OK', {
            duration: 6000,
          });
        },
      });
  }

  private apply(data: SystemSettingsResponse): void {
    this.data.set(data);
    const s = data.settings;
    this.form.reset({
      associationName: s.associationName,
      associationShortName: s.associationShortName,
      associationAddress: s.associationAddress ?? '',
      associationPhone: s.associationPhone ?? '',
      associationEmail: s.associationEmail ?? '',
      sessionDays: s.sessionDays,
      otpTtlMinutes: s.otpTtlMinutes,
      otpMaxAttempts: s.otpMaxAttempts,
      otpResendSeconds: s.otpResendSeconds,
      otpMaxPerHour: s.otpMaxPerHour,
      maintenanceEnabled: s.maintenanceEnabled,
      maintenanceMessage: s.maintenanceMessage ?? '',
    });
  }
}
