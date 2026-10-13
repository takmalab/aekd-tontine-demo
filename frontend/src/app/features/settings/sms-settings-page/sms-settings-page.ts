import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PhoneInput } from '../../../shared/components/phone-input/phone-input';
import { formatPhone } from '../../../shared/utils/countries';
import { SmsProviderInfo, SmsSettings, SmsTestResponse } from '../sms-settings.model';
import { SmsSettingsService } from '../sms-settings.service';

/**
 * Paramètres SMS (ADMIN, docs/decisions.md n° 48) : choix du fournisseur
 * utilisé pour les codes de connexion, secours automatique et SMS de test.
 * Les clés API restent sur le serveur et ne sont jamais affichées ici.
 */
@Component({
  selector: 'app-sms-settings-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    MatSelectModule,
    MatSlideToggleModule,
    PhoneInput,
  ],
  templateUrl: './sms-settings-page.html',
  styleUrl: './sms-settings-page.scss',
})
export class SmsSettingsPage implements OnInit {
  private readonly service = inject(SmsSettingsService);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly saving = signal(false);
  readonly settings = signal<SmsSettings | null>(null);

  /** Choix en cours d'édition (pas encore enregistré). */
  readonly selectedProvider = signal<string | null>(null);
  readonly fallbackEnabled = signal(true);
  readonly dirty = computed(() => {
    const saved = this.settings();
    return (
      !!saved &&
      (saved.fromConfiguration ||
        saved.activeProvider !== this.selectedProvider() ||
        saved.fallbackEnabled !== this.fallbackEnabled())
    );
  });
  readonly configuredProviders = computed(() => (this.settings()?.providers ?? []).filter((p) => p.configured));

  readonly testPhone = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly testProvider = new FormControl<string | null>(null);
  readonly testing = signal(false);
  readonly testResult = signal<SmsTestResponse | null>(null);
  readonly testError = signal<string | null>(null);
  readonly formatPhone = formatPhone;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.service.get().subscribe({
      next: (settings) => {
        this.apply(settings);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  label(id: string): string {
    return this.settings()?.providers.find((p) => p.id === id)?.label ?? id;
  }

  statusOf(provider: SmsProviderInfo): { text: string; tone: 'ok' | 'warn' | 'info' } {
    if (provider.simulation) {
      return { text: 'Simulation', tone: 'info' };
    }
    return provider.configured ? { text: 'Configuré', tone: 'ok' } : { text: 'Clé API absente', tone: 'warn' };
  }

  save(): void {
    const provider = this.selectedProvider();
    if (!provider) {
      return;
    }
    this.saving.set(true);
    this.service.update({ activeProvider: provider, fallbackEnabled: this.fallbackEnabled() }).subscribe({
      next: (settings) => {
        this.apply(settings);
        this.saving.set(false);
        this.snackBar.open(`Fournisseur SMS : ${this.label(settings.activeProvider)}`, 'OK', { duration: 4000 });
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.snackBar.open(error.error?.message || "L'enregistrement a échoué.", 'OK', { duration: 6000 });
      },
    });
  }

  sendTest(): void {
    if (this.testPhone.invalid) {
      this.testPhone.markAsTouched();
      return;
    }
    this.testing.set(true);
    this.testResult.set(null);
    this.testError.set(null);
    this.service.sendTest({ phone: this.testPhone.value, provider: this.testProvider.value }).subscribe({
      next: (result) => {
        this.testing.set(false);
        this.testResult.set(result);
      },
      error: (error: HttpErrorResponse) => {
        this.testing.set(false);
        this.testError.set(error.error?.message || "L'envoi du SMS de test a échoué.");
      },
    });
  }

  private apply(settings: SmsSettings): void {
    this.settings.set(settings);
    this.selectedProvider.set(settings.activeProvider);
    this.fallbackEnabled.set(settings.fallbackEnabled);
    if (!this.testProvider.value) {
      this.testProvider.setValue(settings.activeProvider);
    }
  }
}
