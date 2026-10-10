package cm.aekd.tontine.tournament;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Set;

/**
 * Règle de date d'un tournoi : « n-ième jour de semaine du mois » (ex. 1er samedi,
 * dernier dimanche), un passage par mois à partir du mois de départ.
 */
public final class TournamentCalendar {

    /** 1 à 4, ou -1 pour le dernier jour de ce type dans le mois. */
    public static final Set<Integer> ALLOWED_WEEKS = Set.of(1, 2, 3, 4, -1);

    private static final DateTimeFormatter LABEL_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private TournamentCalendar() {
    }

    public static LocalDate dateFor(YearMonth month, int weekOfMonth, DayOfWeek dayOfWeek) {
        if (!ALLOWED_WEEKS.contains(weekOfMonth)) {
            throw new IllegalArgumentException("Rang de semaine invalide : " + weekOfMonth);
        }
        return month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(weekOfMonth, dayOfWeek));
    }

    /** Date prévue du passage {@code position} (0 = mois de départ). */
    public static LocalDate dateFor(YearMonth startMonth, int position, int weekOfMonth, DayOfWeek dayOfWeek) {
        return dateFor(startMonth.plusMonths(position), weekOfMonth, dayOfWeek);
    }

    /** Libellé décidé le 2026-10-05 : « Séance de novembre 2026 ». */
    public static String sessionLabel(LocalDate date) {
        return "Séance de " + date.format(LABEL_MONTH);
    }
}
