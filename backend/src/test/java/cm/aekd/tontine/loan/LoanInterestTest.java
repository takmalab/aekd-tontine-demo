package cm.aekd.tontine.loan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Règles validées le 2026-10-14 (docs/decisions.md n° 14 et 19). */
class LoanInterestTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void monthlySimpleInterestOnTheInitialCapital() {
        // Exemple de la décision : 300 000 FCFA sur 6 mois à 5 % par mois.
        assertThat(LoanInterest.interest(bd("300000"), bd("5"), 6)).isEqualByComparingTo("90000");
    }

    @Test
    void roundedUpToTheNextFiveFrancs() {
        // 123 457 × 1,5 % × 1 = 1 851,855 → 1 855
        assertThat(LoanInterest.interest(bd("123457"), bd("1.5"), 1)).isEqualByComparingTo("1855");
        // 100 001 × 1 % × 1 = 1 000,01 → 1 005
        assertThat(LoanInterest.interest(bd("100001"), bd("1"), 1)).isEqualByComparingTo("1005");
        // Déjà multiple de 5 : inchangé.
        assertThat(LoanInterest.interest(bd("100000"), bd("2"), 3)).isEqualByComparingTo("6000");
    }

    @Test
    void ratePerPeriodIsProrated() {
        // decisions.md n° 53 : 5 % tous les 2 mois. Sur 2 mois → 5 % ; sur 3 mois → 7,5 %.
        assertThat(LoanInterest.interest(bd("300000"), bd("5"), 2, 2)).isEqualByComparingTo("15000");
        assertThat(LoanInterest.interest(bd("300000"), bd("5"), 3, 2)).isEqualByComparingTo("22500");
        // Trimestriel : 6 % par trimestre sur 1 mois → 2 %.
        assertThat(LoanInterest.interest(bd("100000"), bd("6"), 1, 3)).isEqualByComparingTo("2000");
        assertThat(LoanInterest.periodLabel(2)).isEqualTo("tous les 2 mois");
        assertThat(LoanInterest.periodLabel(3)).isEqualTo("par trimestre");
    }

    @Test
    void noRateMeansNoInterest() {
        assertThat(LoanInterest.interest(bd("300000"), null, 6)).isEqualByComparingTo("0");
        assertThat(LoanInterest.interest(bd("300000"), bd("0"), 6)).isEqualByComparingTo("0");
    }

    @Test
    void repaymentsAreSplitProportionally() {
        // 390 000 dus dont 300 000 de capital : 50 000 remboursés contiennent 38 461 de capital.
        assertThat(LoanInterest.capitalShare(bd("50000"), bd("300000"), bd("390000"))).isEqualByComparingTo("38461");
        // Tout remboursé : tout le capital est revenu.
        assertThat(LoanInterest.capitalShare(bd("390000"), bd("300000"), bd("390000"))).isEqualByComparingTo("300000");
        // Sans intérêts : tout remboursement est du capital.
        assertThat(LoanInterest.capitalShare(bd("50000"), bd("300000"), bd("300000"))).isEqualByComparingTo("50000");
    }
}
