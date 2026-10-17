package cm.aekd.tontine.tournament;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TournamentCalendarTest {

    @Test
    void computesNthWeekdayOfMonth() {
        // Novembre 2026 commence un dimanche.
        assertThat(TournamentCalendar.dateFor(YearMonth.of(2026, 11), 1, DayOfWeek.SATURDAY))
                .isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(TournamentCalendar.dateFor(YearMonth.of(2026, 11), 1, DayOfWeek.SUNDAY))
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(TournamentCalendar.dateFor(YearMonth.of(2026, 11), 3, DayOfWeek.SATURDAY))
                .isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(TournamentCalendar.dateFor(YearMonth.of(2026, 11), -1, DayOfWeek.SUNDAY))
                .isEqualTo(LocalDate.of(2026, 11, 29));
    }

    @Test
    void positionAddsOneMonthPerPassage() {
        YearMonth start = YearMonth.of(2026, 11);
        assertThat(TournamentCalendar.dateFor(start, 1, 1, DayOfWeek.SATURDAY)).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(TournamentCalendar.dateFor(start, 2, 1, DayOfWeek.SATURDAY)).isEqualTo(LocalDate.of(2027, 1, 2));
    }

    @Test
    void rejectsUnsupportedWeekRank() {
        assertThatThrownBy(() -> TournamentCalendar.dateFor(YearMonth.of(2026, 11), 5, DayOfWeek.SATURDAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelUsesFrenchMonthName() {
        assertThat(TournamentCalendar.sessionLabel(LocalDate.of(2026, 11, 7))).isEqualTo("Séance du 7 novembre 2026");
        assertThat(TournamentCalendar.sessionLabel(LocalDate.of(2027, 2, 6))).isEqualTo("Séance du 6 février 2027");
    }
}
