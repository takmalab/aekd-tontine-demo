package cm.aekd.tontine.savings;

import cm.aekd.tontine.savings.SavingsInterestCalculator.Rate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Intérêts simples, taux mensuel, par mois complet (docs/decisions.md n° 39). */
class SavingsInterestCalculatorTest {

    private static final List<Rate> ONE_PERCENT = List.of(new Rate(LocalDate.of(2026, 1, 1), new BigDecimal("1.000")));

    @Test
    void countsOnlyCompleteMonthsSinceValidation() {
        LocalDate validated = LocalDate.of(2026, 6, 15);
        assertThat(SavingsInterestCalculator.completeMonths(validated, LocalDate.of(2026, 7, 14))).isZero();
        assertThat(SavingsInterestCalculator.completeMonths(validated, LocalDate.of(2026, 7, 15))).isEqualTo(1);
        assertThat(SavingsInterestCalculator.completeMonths(validated, LocalDate.of(2026, 9, 20))).isEqualTo(3);
    }

    @Test
    void simpleInterestIsAmountTimesRateTimesCompleteMonths() {
        var accrual = SavingsInterestCalculator.accrue(new BigDecimal(100000), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 9, 20), ONE_PERCENT);

        assertThat(accrual.completeMonths()).isEqualTo(3);
        assertThat(accrual.interest()).isEqualByComparingTo("3000");
    }

    @Test
    void eachMonthUsesTheRateInForceWhenItEnds() {
        List<Rate> rates = List.of(
                new Rate(LocalDate.of(2026, 1, 1), new BigDecimal("1.000")),
                new Rate(LocalDate.of(2026, 8, 1), new BigDecimal("2.000")));

        // Mois terminés le 15/07 (1 %), le 15/08 (2 %) et le 15/09 (2 %).
        var accrual = SavingsInterestCalculator.accrue(new BigDecimal(100000), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 9, 20), rates);

        assertThat(accrual.interest()).isEqualByComparingTo("5000");
    }

    @Test
    void monthsEndingBeforeTheFirstRateEarnNothing() {
        List<Rate> rates = List.of(new Rate(LocalDate.of(2026, 9, 1), new BigDecimal("1.500")));

        var accrual = SavingsInterestCalculator.accrue(new BigDecimal(20000), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 9, 20), rates);

        assertThat(accrual.completeMonths()).isEqualTo(3);
        assertThat(accrual.interest()).isEqualByComparingTo("300");
    }

    @Test
    void roundsToTheNearestFranc() {
        var accrual = SavingsInterestCalculator.accrue(new BigDecimal(1050), LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1), List.of(new Rate(LocalDate.of(2026, 1, 1), new BigDecimal("0.750"))));

        // 1 050 × 0,75 % = 7,875 -> 8 FCFA
        assertThat(accrual.interest()).isEqualByComparingTo("8");
    }
}
