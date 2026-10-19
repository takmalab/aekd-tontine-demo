import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Component, OnDestroy, OnInit, WritableSignal, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { AEKD_THEME, AppTheme, ThemePattern, ThemeService } from '../../../core/services/theme.service';
import { readableOnWhite } from '../../../shared/utils/theme-colors';

/** Thème prêt à appliquer : seulement les couleurs, le reste est conservé. */
interface Preset {
  name: string;
  primary: string;
  secondary: string;
  dark: string;
}

const HEX = /^#[0-9a-fA-F]{6}$/;

/**
 * « Apparence » (super administrateur, décision n° 72) : couleurs, polices, motif,
 * logo, slogan et photo de la page de connexion. Toute l'application s'affiche en
 * aperçu pendant le réglage ; quitter la page sans enregistrer rétablit le thème.
 */
@Component({
  selector: 'app-theme-settings-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './theme-settings-page.html',
  styleUrls: ['../sms-settings-page/sms-settings-page.scss', './theme-settings-page.scss'],
})
export class ThemeSettingsPage implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);
  private readonly document = inject(DOCUMENT);
  protected readonly themeService = inject(ThemeService);

  readonly saving = signal(false);
  readonly logoBusy = signal(false);
  readonly photoBusy = signal(false);

  readonly presets: Preset[] = [
    { name: 'AEKD', primary: AEKD_THEME.primaryColor, secondary: AEKD_THEME.secondaryColor, dark: AEKD_THEME.darkColor },
    { name: 'Forêt', primary: '#2e7d32', secondary: '#8d6e3f', dark: '#263238' },
    { name: 'Océan', primary: '#0b6fb8', secondary: '#0f766e', dark: '#1e293b' },
    { name: 'Royal', primary: '#6d28d9', secondary: '#b45309', dark: '#2e1065' },
    { name: 'Bordeaux', primary: '#9f1239', secondary: '#a16207', dark: '#3f1d2b' },
    { name: 'Terre', primary: '#b45309', secondary: '#7c2d12', dark: '#292524' },
  ];

  readonly patterns: { value: ThemePattern; label: string; hint: string }[] = [
    { value: 'MASQUES', label: 'Motifs africains', hint: 'Masques et symboles (AEKD)' },
    { value: 'SYMBOLES', label: 'Symboles', hint: 'Plus discret, géométrique' },
    { value: 'AUCUN', label: 'Aucun', hint: 'Fonds unis' },
  ];

  readonly form = this.fb.nonNullable.group({
    primaryColor: ['', [Validators.required, Validators.pattern(HEX)]],
    secondaryColor: ['', [Validators.required, Validators.pattern(HEX)]],
    darkColor: ['', [Validators.required, Validators.pattern(HEX)]],
    headingFont: ['', Validators.required],
    bodyFont: ['', Validators.required],
    pattern: ['MASQUES' as ThemePattern, Validators.required],
    slogan: ['', Validators.maxLength(150)],
  });

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** Thème du formulaire (aperçu). */
  readonly draft = computed<AppTheme>(() => {
    const v = { ...this.form.getRawValue(), ...this.value() };
    const saved = this.themeService.saved();
    return {
      ...saved,
      primaryColor: HEX.test(v.primaryColor ?? '') ? v.primaryColor!.toLowerCase() : saved.primaryColor,
      secondaryColor: HEX.test(v.secondaryColor ?? '') ? v.secondaryColor!.toLowerCase() : saved.secondaryColor,
      darkColor: HEX.test(v.darkColor ?? '') ? v.darkColor!.toLowerCase() : saved.darkColor,
      headingFont: v.headingFont || saved.headingFont,
      bodyFont: v.bodyFont || saved.bodyFont,
      pattern: (v.pattern as ThemePattern) || saved.pattern,
      slogan: v.slogan?.trim() || AEKD_THEME.slogan,
    };
  });

  /** Couleurs trop claires pour du texte ou des boutons blancs : avertissement, pas blocage. */
  readonly lightColors = computed(() => {
    const d = this.draft();
    const names: string[] = [];
    if (!readableOnWhite(d.primaryColor)) names.push('principale');
    if (!readableOnWhite(d.secondaryColor)) names.push('secondaire');
    if (!readableOnWhite(d.darkColor)) names.push('sombre');
    return names;
  });

  readonly dirty = computed(() => {
    const d = this.draft();
    const s = this.themeService.saved();
    return (['primaryColor', 'secondaryColor', 'darkColor', 'headingFont', 'bodyFont', 'pattern', 'slogan'] as const).some(
      (k) => d[k] !== s[k],
    );
  });

  constructor() {
    // Aperçu en direct dans toute l'application.
    this.form.valueChanges.subscribe(() => this.themeService.preview(this.draft()));
  }

  ngOnInit(): void {
    this.reset(this.themeService.saved());
    // Thème du serveur le plus récent (un autre appareil a pu le changer) et liste des polices.
    this.http.get<AppTheme>(`${environment.apiUrl}/public/theme`).subscribe((theme) => {
      this.themeService.setSaved(theme);
      this.reset(theme);
      this.loadAllFonts(theme.fonts);
    });
  }

  ngOnDestroy(): void {
    this.themeService.cancelPreview();
  }

  applyPreset(p: Preset): void {
    this.form.patchValue({ primaryColor: p.primary, secondaryColor: p.secondary, darkColor: p.dark });
    this.form.markAsDirty();
  }

  /** Thème AEKD complet (couleurs, polices, motif, slogan). Le logo et la photo restent à part. */
  restoreAekd(): void {
    this.form.patchValue({
      primaryColor: AEKD_THEME.primaryColor,
      secondaryColor: AEKD_THEME.secondaryColor,
      darkColor: AEKD_THEME.darkColor,
      headingFont: AEKD_THEME.headingFont,
      bodyFont: AEKD_THEME.bodyFont,
      pattern: AEKD_THEME.pattern,
      slogan: AEKD_THEME.slogan,
    });
    this.form.markAsDirty();
  }

  cancel(): void {
    this.reset(this.themeService.saved());
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const d = this.draft();
    const body = {
      primaryColor: d.primaryColor,
      secondaryColor: d.secondaryColor,
      darkColor: d.darkColor,
      headingFont: d.headingFont,
      bodyFont: d.bodyFont,
      pattern: d.pattern,
      slogan: this.form.controls.slogan.value.trim() || null,
    };
    this.http.put<AppTheme>(`${environment.apiUrl}/settings/theme`, body).subscribe({
      next: (theme) => {
        this.saving.set(false);
        this.themeService.setSaved(theme);
        this.reset(theme);
        this.snackBar.open('Apparence enregistrée', 'OK', { duration: 4000 });
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.snackBar.open(error.error?.message || "L'apparence n'a pas pu être enregistrée.", 'OK', { duration: 6000 });
      },
    });
  }

  uploadLogo(event: Event): void {
    const file = this.pickFile(event);
    if (!file) return;
    this.logoBusy.set(true);
    this.http.post(`${environment.apiUrl}/settings/system/logo`, this.formData(file)).subscribe({
      next: () => this.afterImage(this.logoBusy, 'Logo mis à jour'),
      error: (e: HttpErrorResponse) => this.imageError(this.logoBusy, e, "Le logo n'a pas pu être enregistré."),
    });
  }

  removeLogo(): void {
    this.logoBusy.set(true);
    this.http.delete(`${environment.apiUrl}/settings/system/logo`).subscribe({
      next: () => this.afterImage(this.logoBusy, 'Logo AEKD rétabli'),
      error: (e: HttpErrorResponse) => this.imageError(this.logoBusy, e, "Le logo n'a pas pu être retiré."),
    });
  }

  uploadPhoto(event: Event): void {
    const file = this.pickFile(event);
    if (!file) return;
    this.photoBusy.set(true);
    this.http.post(`${environment.apiUrl}/settings/theme/login-image`, this.formData(file)).subscribe({
      next: () => this.afterImage(this.photoBusy, 'Photo de connexion mise à jour'),
      error: (e: HttpErrorResponse) => this.imageError(this.photoBusy, e, "La photo n'a pas pu être enregistrée."),
    });
  }

  removePhoto(): void {
    this.photoBusy.set(true);
    this.http.delete(`${environment.apiUrl}/settings/theme/login-image`).subscribe({
      next: () => this.afterImage(this.photoBusy, 'Photo par défaut rétablie'),
      error: (e: HttpErrorResponse) => this.imageError(this.photoBusy, e, "La photo n'a pas pu être retirée."),
    });
  }

  fontFamily(font: string): string {
    return `'${font}', sans-serif`;
  }

  private reset(theme: AppTheme): void {
    this.form.reset({
      primaryColor: theme.primaryColor,
      secondaryColor: theme.secondaryColor,
      darkColor: theme.darkColor,
      headingFont: theme.headingFont,
      bodyFont: theme.bodyFont,
      pattern: theme.pattern,
      slogan: theme.slogan,
    });
  }

  /** Les images changent la version du thème : on recharge le thème enregistré, l'aperçu en cours est gardé. */
  private afterImage(busy: WritableSignal<boolean>, message: string): void {
    this.http.get<AppTheme>(`${environment.apiUrl}/public/theme`).subscribe((theme) => {
      busy.set(false);
      this.themeService.setSaved(theme);
      this.themeService.preview(this.draft());
      this.snackBar.open(message, 'OK', { duration: 4000 });
    });
  }

  private imageError(busy: WritableSignal<boolean>, error: HttpErrorResponse, fallback: string): void {
    busy.set(false);
    this.snackBar.open(error.error?.message || fallback, 'OK', { duration: 6000 });
  }

  private pickFile(event: Event): File | null {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    return file;
  }

  private formData(file: File): FormData {
    const body = new FormData();
    body.append('file', file);
    return body;
  }

  /** Toutes les polices proposées, pour les voir dans la liste. */
  private loadAllFonts(fonts: string[]): void {
    const families = fonts.map((f) => `family=${f.replace(/ /g, '+')}:wght@400;700`).join('&');
    const link = this.document.createElement('link');
    link.rel = 'stylesheet';
    link.href = `https://fonts.googleapis.com/css2?${families}&display=swap`;
    this.document.head.appendChild(link);
  }
}
