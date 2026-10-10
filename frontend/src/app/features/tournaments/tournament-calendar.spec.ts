import { nthWeekdayOfMonth, plannedDate, upcomingMonths } from './tournament-calendar';

describe('tournament-calendar', () => {
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

  it('computes the n-th weekday of a month (November 2026 starts on a Sunday)', () => {
    expect(iso(nthWeekdayOfMonth(2026, 10, 1, 'SATURDAY'))).toBe('2026-11-07');
    expect(iso(nthWeekdayOfMonth(2026, 10, 1, 'SUNDAY'))).toBe('2026-11-01');
    expect(iso(nthWeekdayOfMonth(2026, 10, 3, 'SATURDAY'))).toBe('2026-11-21');
  });

  it('computes the last weekday of a month', () => {
    expect(iso(nthWeekdayOfMonth(2026, 10, -1, 'SUNDAY'))).toBe('2026-11-29');
    expect(iso(nthWeekdayOfMonth(2026, 10, -1, 'MONDAY'))).toBe('2026-11-30');
  });

  it('adds one month per position, across years', () => {
    expect(iso(plannedDate('2026-11', 1, 1, 'SATURDAY'))).toBe('2026-12-05');
    expect(iso(plannedDate('2026-11', 2, 1, 'SATURDAY'))).toBe('2027-01-02');
  });

  it('lists upcoming months from the current one', () => {
    expect(upcomingMonths(3, new Date(2026, 10, 15))).toEqual(['2026-11', '2026-12', '2027-01']);
  });
});
