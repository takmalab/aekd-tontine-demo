import { contrast, mix, onColor, readableOnWhite, themeVariables } from './theme-colors';

/** Nuances du thème (décision n° 72). */
describe('themeVariables', () => {
  const aekd = themeVariables({ primary: '#e86e09', secondary: '#ab583a', dark: '#36353a' });

  it('gives back the AEKD shades of styles.scss, within a few units', () => {
    const close = (a: string, b: string) => {
      const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
      const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
      return pa.every((v, i) => Math.abs(v - pb[i]) <= 8);
    };
    expect(close(aekd['--aekd-orange-dark'], '#c95d05')).toBe(true);
    expect(close(aekd['--aekd-orange-soft'], '#fdeddf')).toBe(true);
    expect(close(aekd['--aekd-brown-dark'], '#8c4529')).toBe(true);
    expect(close(aekd['--aekd-brown-soft'], '#f6e8e2')).toBe(true);
    expect(close(aekd['--aekd-gray-dark'], '#2a292d')).toBe(true);
    expect(close(aekd['--aekd-gray-muted'], '#6b6a70')).toBe(true);
  });

  it('feeds the Material tokens with the three colours', () => {
    const v = themeVariables({ primary: '#0b6fb8', secondary: '#0f766e', dark: '#1e293b' });
    expect(v['--mat-sys-primary']).toBe('#0b6fb8');
    expect(v['--mat-sys-secondary']).toBe('#0f766e');
    expect(v['--mat-sys-on-surface']).toBe('#1e293b');
    expect(v['--aekd-orange']).toBe('#0b6fb8');
  });

  it('keeps text readable on the primary colour', () => {
    expect(aekd['--mat-sys-on-primary']).toBe('#ffffff');
    // Jaune clair : texte sombre plutôt que blanc.
    expect(onColor('#ffe066', '#36353a')).toBe('#36353a');
    expect(readableOnWhite('#ffe066')).toBe(false);
    expect(readableOnWhite('#e86e09')).toBe(true);
  });

  it('lightens the primary colour used as text on the dark colour', () => {
    // Orange AEKD : assez contrasté, inchangé.
    expect(aekd['--aekd-orange-on-dark']).toBe('#e86e09');
    const ocean = themeVariables({ primary: '#0b6fb8', secondary: '#0f766e', dark: '#1e293b' });
    expect(ocean['--aekd-orange-on-dark']).not.toBe('#0b6fb8');
    expect(contrast(ocean['--aekd-orange-on-dark'], '#1e293b')).toBeGreaterThanOrEqual(3.5);
  });

  it('mixes and measures colours', () => {
    expect(mix('#000000', [255, 255, 255], 0.5)).toBe('#808080');
    expect(contrast('#000000', '#ffffff')).toBeCloseTo(21, 0);
  });
});
