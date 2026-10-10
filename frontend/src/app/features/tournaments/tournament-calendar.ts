import { DayOfWeek, WeekOfMonth } from './tournament.model';

/** Index JavaScript (Date.getDay) de chaque jour : dimanche = 0. */
const JS_DAY: Record<DayOfWeek, number> = {
  SUNDAY: 0,
  MONDAY: 1,
  TUESDAY: 2,
  WEDNESDAY: 3,
  THURSDAY: 4,
  FRIDAY: 5,
  SATURDAY: 6,
};

/**
 * Même règle que TournamentCalendar (backend), pour l'aperçu dans le formulaire :
 * n-ième jour de semaine du mois (`month` de 0 à 11), ou le dernier si -1.
 */
export function nthWeekdayOfMonth(year: number, month: number, week: WeekOfMonth, day: DayOfWeek): Date {
  const target = JS_DAY[day];
  if (week === -1) {
    const last = new Date(year, month + 1, 0);
    return new Date(year, month, last.getDate() - ((last.getDay() - target + 7) % 7));
  }
  const first = new Date(year, month, 1);
  return new Date(year, month, 1 + ((target - first.getDay() + 7) % 7) + (week - 1) * 7);
}

/** Date prévue du passage `position` (0 = mois de départ "YYYY-MM"). */
export function plannedDate(startMonth: string, position: number, week: WeekOfMonth, day: DayOfWeek): Date {
  const [year, month] = startMonth.split('-').map(Number);
  return nthWeekdayOfMonth(year, month - 1 + position, week, day);
}

/** Mois de départ proposés : le mois courant et les `count - 1` suivants, au format "YYYY-MM". */
export function upcomingMonths(count: number, from: Date = new Date()): string[] {
  return Array.from({ length: count }, (_, i) => {
    const d = new Date(from.getFullYear(), from.getMonth() + i, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  });
}
