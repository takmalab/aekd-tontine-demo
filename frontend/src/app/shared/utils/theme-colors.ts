/**
 * Nuances du thème (décision n° 72) : à partir de trois couleurs (principale,
 * secondaire, sombre), toutes les variables CSS de l'application — celles de
 * l'AEKD (--aekd-*) et les jetons Material (--mat-sys-*).
 * Les coefficients redonnent, pour les couleurs AEKD, les nuances d'origine de
 * styles.scss (#c95d05, #fdeddf, #8c4529…), à quelques unités près.
 */
export interface ThemeColors {
  primary: string;
  secondary: string;
  dark: string;
}

type Rgb = [number, number, number];

const WHITE: Rgb = [255, 255, 255];
const BLACK: Rgb = [0, 0, 0];

export function hexToRgb(hex: string): Rgb {
  const h = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as Rgb;
}

export function rgbToHex([r, g, b]: Rgb): string {
  return '#' + [r, g, b].map((v) => Math.round(Math.min(255, Math.max(0, v))).toString(16).padStart(2, '0')).join('');
}

/** Mélange : `weight` de la couleur `a`, le reste de `b`. */
export function mix(a: string | Rgb, b: Rgb, weight: number): string {
  const ca = typeof a === 'string' ? hexToRgb(a) : a;
  return rgbToHex(ca.map((v, i) => v * weight + b[i] * (1 - weight)) as Rgb);
}

/** Luminance relative (WCAG). */
export function luminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Rapport de contraste (WCAG) entre deux couleurs. */
export function contrast(a: string, b: string): number {
  const [l1, l2] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (l1 + 0.05) / (l2 + 0.05);
}

/**
 * Texte posé sur une couleur : blanc tant qu'il reste lisible (contraste ≥ 3, seuil
 * des gros textes et boutons, celui de l'orange AEKD), sinon la couleur sombre.
 */
export function onColor(background: string, dark: string): string {
  return contrast(background, '#ffffff') >= 3 ? '#ffffff' : dark;
}

/** Couleurs trop claires pour du texte sur fond blanc (liens, titres) : signalées dans l'écran. */
export function readableOnWhite(hex: string): boolean {
  return contrast(hex, '#ffffff') >= 3;
}

export function themeVariables({ primary, secondary, dark }: ThemeColors): Record<string, string> {
  const primaryDark = mix(primary, BLACK, 0.87);
  const primarySoft = mix(primary, WHITE, 0.12);
  const secondaryDark = mix(secondary, BLACK, 0.82);
  const secondarySoft = mix(secondary, WHITE, 0.12);
  const darkDark = mix(dark, BLACK, 0.78);
  const muted = mix(dark, WHITE, 0.72);
  const onPrimary = onColor(primary, dark);
  // Texte de la couleur principale sur fond sombre (barre du haut, bannières, photo de connexion).
  let primaryOnDark = primary;
  for (let i = 1; i <= 8 && contrast(primaryOnDark, dark) < 3.5; i++) {
    primaryOnDark = mix(primary, WHITE, 1 - i * 0.1);
  }
  const onSecondary = onColor(secondary, dark);

  return {
    // Identité (styles.scss, :root).
    '--aekd-orange': primary,
    '--aekd-orange-dark': primaryDark,
    '--aekd-orange-soft': primarySoft,
    '--aekd-brown': secondary,
    '--aekd-brown-dark': secondaryDark,
    '--aekd-brown-soft': secondarySoft,
    '--aekd-gray': dark,
    '--aekd-gray-dark': darkDark,
    '--aekd-gray-muted': muted,
    '--aekd-on-orange': onPrimary,
    '--aekd-orange-on-dark': primaryOnDark,

    // Jetons Material (mat.theme-overrides de styles.scss).
    '--mat-sys-primary': primary,
    '--mat-sys-on-primary': onPrimary,
    '--mat-sys-primary-container': primarySoft,
    '--mat-sys-on-primary-container': dark,
    '--mat-sys-inverse-primary': mix(primary, WHITE, 0.55),
    '--mat-sys-primary-fixed': primarySoft,
    '--mat-sys-primary-fixed-dim': mix(primary, WHITE, 0.3),
    '--mat-sys-on-primary-fixed': dark,
    '--mat-sys-on-primary-fixed-variant': secondaryDark,
    '--mat-sys-surface-tint': primary,

    '--mat-sys-secondary': secondary,
    '--mat-sys-on-secondary': onSecondary,
    '--mat-sys-secondary-container': secondarySoft,
    '--mat-sys-on-secondary-container': dark,
    '--mat-sys-secondary-fixed': secondarySoft,
    '--mat-sys-secondary-fixed-dim': mix(secondary, WHITE, 0.2),
    '--mat-sys-on-secondary-fixed': dark,
    '--mat-sys-on-secondary-fixed-variant': secondaryDark,

    '--mat-sys-tertiary': dark,
    '--mat-sys-on-tertiary-container': dark,
    '--mat-sys-on-tertiary-fixed': dark,
    '--mat-sys-on-tertiary-fixed-variant': dark,

    '--mat-sys-error': secondaryDark,
    '--mat-sys-error-container': secondarySoft,
    '--mat-sys-on-error-container': secondaryDark,

    '--mat-sys-on-background': dark,
    '--mat-sys-on-surface': dark,
    '--mat-sys-on-surface-variant': muted,
    '--mat-sys-inverse-surface': dark,
  };
}
