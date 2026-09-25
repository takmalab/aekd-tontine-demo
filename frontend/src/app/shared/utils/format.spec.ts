import { TestBed } from '@angular/core/testing';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { FrDateAdapter } from './fr-date-adapter';
import { formatFcfa, fromIsoDate, toIsoDate } from './format';

describe('format utils', () => {
  it('formate les montants en FCFA', () => {
    expect(formatFcfa(62000).replace(/\s/g, ' ')).toBe('62 000 FCFA');
    expect(formatFcfa(null)).toBe('—');
  });

  it('convertit Date <-> LocalDate sans décalage de fuseau', () => {
    expect(toIsoDate(new Date(2026, 9, 1))).toBe('2026-10-01');
    const d = fromIsoDate('2026-10-31');
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 9, 31]);
  });
});

describe('FrDateAdapter', () => {
  let adapter: FrDateAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FrDateAdapter, { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' }],
    });
    adapter = TestBed.inject(FrDateAdapter);
  });

  it('lit une date saisie au format JJ/MM/AAAA', () => {
    const d = adapter.parse('01/10/2026', null)!;
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 9, 1]);
  });

  it('rejette une date impossible ou mal formée', () => {
    expect(adapter.isValid(adapter.parse('31/02/2026', null)!)).toBe(false);
    expect(adapter.isValid(adapter.parse('2026/10/01', null)!)).toBe(false);
  });

  it('retourne null pour une saisie vide', () => {
    expect(adapter.parse('  ', null)).toBeNull();
  });
});
