import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { themeVariables } from '../../shared/utils/theme-colors';

/** Thème de l'application (décision n° 72), tel que renvoyé par GET /api/public/theme. */
export interface AppTheme {
  associationName: string;
  associationShortName: string;
  primaryColor: string;
  secondaryColor: string;
  darkColor: string;
  headingFont: string;
  bodyFont: string;
  pattern: ThemePattern;
  slogan: string;
  customLogo: boolean;
  customLoginImage: boolean;
  version: string;
  fonts: string[];
}

export type ThemePattern = 'MASQUES' | 'SYMBOLES' | 'AUCUN';

/** Thème AEKD : valeurs par défaut, identiques à celles du serveur. */
export const AEKD_THEME: AppTheme = {
  associationName: 'Amicale des Enfants Kamkop de Douala',
  associationShortName: 'AEKD',
  primaryColor: '#e86e09',
  secondaryColor: '#ab583a',
  darkColor: '#36353a',
  headingFont: 'Poppins',
  bodyFont: 'Inter',
  pattern: 'MASQUES',
  slogan: 'La solidarité, en toute transparence.',
  customLogo: false,
  customLoginImage: false,
  version: '0',
  fonts: ['Poppins', 'Inter'],
};

/** Mémorisé sur l'appareil : appliqué dès le démarrage (index.html), sans flash des couleurs AEKD. */
const STORAGE_KEY = 'aekd_theme';
/** Variables CSS précalculées, lues par le petit script de index.html. */
const STORAGE_VARS_KEY = 'aekd_theme_vars';
/** Polices déjà chargées par index.html. */
const PRELOADED_FONTS = new Set(['Poppins', 'Inter']);

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  /** Thème enregistré (celui du serveur). */
  readonly saved = signal<AppTheme>(readCache() ?? AEKD_THEME);
  /** Thème affiché : l'enregistré, ou un aperçu en cours dans l'écran « Apparence ». */
  readonly theme = signal<AppTheme>(this.saved());

  readonly shortName = computed(() => this.theme().associationShortName);
  readonly fullName = computed(() => this.theme().associationName);
  /** Logo complet (pages d'accueil, connexion) et logo rond (en-tête, onglet). */
  readonly logoUrl = computed(() => (this.theme().customLogo ? this.logoApiUrl() : 'assets/logo-aekd.png'));
  readonly markUrl = computed(() => (this.theme().customLogo ? this.logoApiUrl() : 'assets/logo-aekd-mark.png'));
  readonly loginImageUrl = computed(() =>
    this.theme().customLoginImage
      ? `${environment.apiUrl}/public/login-image?v=${this.theme().version}`
      : 'assets/login-community.jpg',
  );
  /** Slogan découpé à la première virgule : la fin est mise en valeur (comme « en toute transparence. »). */
  readonly sloganParts = computed(() => {
    const slogan = this.theme().slogan;
    const i = slogan.indexOf(',');
    return i < 0 ? { start: '', end: slogan } : { start: slogan.slice(0, i + 1), end: slogan.slice(i + 1).trim() };
  });

  private readonly loadedFonts = new Set(PRELOADED_FONTS);

  /** Au démarrage : thème mémorisé tout de suite, puis celui du serveur. */
  init(): void {
    this.apply(this.saved());
    this.refresh();
  }

  refresh(): void {
    this.http.get<AppTheme>(`${environment.apiUrl}/public/theme`).subscribe({
      next: (theme) => this.setSaved(theme),
      // Hors ligne ou serveur indisponible : le thème mémorisé reste en place.
      error: () => undefined,
    });
  }

  /** Après un enregistrement dans l'écran « Apparence ». */
  setSaved(theme: AppTheme): void {
    this.saved.set(theme);
    this.apply(theme);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(theme));
      localStorage.setItem(STORAGE_VARS_KEY, JSON.stringify(this.variables(theme)));
    } catch {
      // Stockage indisponible (navigation privée) : sans conséquence.
    }
  }

  /** Aperçu en direct, sans enregistrer. */
  preview(theme: AppTheme): void {
    this.apply(theme);
  }

  /** Fin de l'aperçu : retour au thème enregistré. */
  cancelPreview(): void {
    this.apply(this.saved());
  }

  private apply(theme: AppTheme): void {
    this.theme.set(theme);
    const root = this.document.documentElement;
    for (const [name, value] of Object.entries(this.variables(theme))) {
      root.style.setProperty(name, value);
    }
    this.loadFont(theme.headingFont);
    this.loadFont(theme.bodyFont);
    this.document.title = `${theme.associationShortName} – Tontine`;
    this.setHeadLink('icon', this.markUrl());
    this.document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme.darkColor);
  }

  private variables(theme: AppTheme): Record<string, string> {
    const vars: Record<string, string> = {
      ...themeVariables({ primary: theme.primaryColor, secondary: theme.secondaryColor, dark: theme.darkColor }),
      '--aekd-font-heading': `'${theme.headingFont}', '${theme.bodyFont}', sans-serif`,
      '--aekd-font-body': `'${theme.bodyFont}', 'Roboto', sans-serif`,
      // Motif : symboles partout, ou aucun ; par défaut, la combinaison AEKD (masques et symboles).
      '--aekd-motif-masques':
        theme.pattern === 'SYMBOLES' ? "url('/assets/patterns/aekd-symboles.svg')" : "url('/assets/patterns/aekd-masques.svg')",
      '--aekd-pattern-display': theme.pattern === 'AUCUN' ? 'none' : 'block',
    };
    if (theme.customLoginImage) {
      vars['--aekd-login-image'] = `url('${environment.apiUrl}/public/login-image?v=${theme.version}')`;
    } else {
      vars['--aekd-login-image'] = "url('/assets/login-community.jpg')";
    }
    return vars;
  }

  private logoApiUrl(): string {
    return `${environment.apiUrl}/public/logo?v=${this.theme().version}`;
  }

  /** Police Google chargée à la demande (graisses 400 à 700). */
  private loadFont(family: string): void {
    if (this.loadedFonts.has(family)) {
      return;
    }
    this.loadedFonts.add(family);
    const link = this.document.createElement('link');
    link.rel = 'stylesheet';
    link.href = `https://fonts.googleapis.com/css2?family=${encodeURIComponent(family).replace(/%20/g, '+')}:wght@400;500;600;700&display=swap`;
    this.document.head.appendChild(link);
  }

  private setHeadLink(rel: string, href: string): void {
    let link = this.document.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`);
    if (!link) {
      link = this.document.createElement('link');
      link.rel = rel;
      this.document.head.appendChild(link);
    }
    link.href = href;
  }
}

function readCache(): AppTheme | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? { ...AEKD_THEME, ...(JSON.parse(raw) as AppTheme) } : null;
  } catch {
    return null;
  }
}
