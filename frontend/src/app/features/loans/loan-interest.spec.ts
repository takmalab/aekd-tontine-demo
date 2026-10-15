import { loanInterest } from './loan-interest';

/** Mêmes cas que LoanInterestTest côté serveur. */
describe('loanInterest', () => {
  it('applies a simple monthly rate to the initial capital', () => {
    expect(loanInterest(300000, 5, 6)).toBe(90000);
  });

  it('rounds up to the next 5 FCFA', () => {
    expect(loanInterest(123457, 1.5, 1)).toBe(1855);
    expect(loanInterest(100001, 1, 1)).toBe(1005);
    expect(loanInterest(100000, 2, 3)).toBe(6000);
  });

  it('ignores floating point noise', () => {
    // 100 000 × 0,1 % × 3 = 300 exactement, pas 305.
    expect(loanInterest(100000, 0.1, 3)).toBe(300);
  });

  it('prorates a rate set per period of several months', () => {
    // decisions.md n° 53 : 5 % tous les 2 mois.
    expect(loanInterest(300000, 5, 2, 2)).toBe(15000);
    expect(loanInterest(300000, 5, 3, 2)).toBe(22500);
    expect(loanInterest(100000, 6, 1, 3)).toBe(2000);
  });

  it('returns 0 without a rate or an amount', () => {
    expect(loanInterest(300000, null, 6)).toBe(0);
    expect(loanInterest(300000, 0, 6)).toBe(0);
    expect(loanInterest(null, 5, 6)).toBe(0);
  });
});
