import { describe, expect, it } from 'vitest';
import { toCsvString } from './csv-export';

describe('toCsvString', () => {
  it('returns an empty string for no rows', () => {
    expect(toCsvString([])).toBe('');
  });

  it('builds a header line from the first row keys', () => {
    const csv = toCsvString([{ nom: 'DONGMO Nicolas', montant: 50000 }]);
    expect(csv).toBe('nom;montant\r\nDONGMO Nicolas;50000');
  });

  it('quotes and escapes values containing separators or quotes', () => {
    const csv = toCsvString([{ description: 'Retard "important"; à traiter' }]);
    expect(csv).toBe('description\r\n"Retard ""important""; à traiter"');
  });

  it('renders null/undefined as empty', () => {
    const csv = toCsvString([{ phone: null }]);
    expect(csv).toBe('phone\r\n');
  });
});
