package cm.aekd.tontine.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul des intérêts d'un prêt (docs/decisions.md n° 14 et 19, validés le
 * 2026-10-14) :
 * <ul>
 *   <li>taux <b>mensuel simple</b> : intérêts = capital × taux % × nombre de mois,
 *   toujours sur le capital de départ ;</li>
 *   <li>arrondi <b>aux 5 FCFA supérieurs</b> ;</li>
 *   <li>le membre rembourse capital + intérêts ; chaque remboursement est réparti
 *   <b>proportionnellement</b> entre capital et intérêts.</li>
 * </ul>
 * Exemple : 300 000 FCFA sur 6 mois à 5 % → 90 000 d'intérêts, 390 000 à rembourser.
 *
 * <p>Période du taux (decisions.md n° 53) : le taux peut s'appliquer par période
 * de N mois (bimestriel, trimestriel…). Les intérêts sont alors au prorata :
 * capital × taux % × (durée en mois / N). Ex. 5 % tous les 2 mois sur 2 mois → 5 %,
 * sur 3 mois → 7,5 %.
 */
public final class LoanInterest {

    private static final BigDecimal FIVE = BigDecimal.valueOf(5);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LoanInterest() {
    }

    /** Intérêts dus avec un taux mensuel ; 0 si aucun taux n'est défini. */
    public static BigDecimal interest(BigDecimal capital, BigDecimal monthlyRatePercent, int months) {
        return interest(capital, monthlyRatePercent, months, 1);
    }

    /** Intérêts dus avec un taux par période de {@code periodMonths} mois, au prorata de la durée. */
    public static BigDecimal interest(BigDecimal capital, BigDecimal ratePercent, int months, int periodMonths) {
        if (capital == null || ratePercent == null || ratePercent.signum() <= 0 || months <= 0) {
            return BigDecimal.ZERO;
        }
        int period = Math.max(1, periodMonths);
        BigDecimal raw = capital.multiply(ratePercent)
                .multiply(BigDecimal.valueOf(months))
                .divide(HUNDRED.multiply(BigDecimal.valueOf(period)), 10, RoundingMode.HALF_UP);
        return roundUpToFive(raw);
    }

    /** « par mois », « tous les 2 mois », « par trimestre »… pour les libellés. */
    public static String periodLabel(Integer periodMonths) {
        int period = periodMonths == null ? 1 : periodMonths;
        return switch (period) {
            case 1 -> "par mois";
            case 3 -> "par trimestre";
            case 6 -> "par semestre";
            case 12 -> "par an";
            default -> "tous les " + period + " mois";
        };
    }

    /**
     * Part de capital contenue dans un montant remboursé (répartition
     * proportionnelle). Arrondie au franc inférieur : l'argent considéré comme
     * de nouveau disponible pour prêter n'est jamais surestimé.
     */
    public static BigDecimal capitalShare(BigDecimal repaid, BigDecimal capital, BigDecimal totalDue) {
        if (repaid == null || repaid.signum() == 0 || totalDue == null || totalDue.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return repaid.multiply(capital).divide(totalDue, 0, RoundingMode.DOWN);
    }

    static BigDecimal roundUpToFive(BigDecimal amount) {
        return amount.divide(FIVE, 0, RoundingMode.CEILING).multiply(FIVE);
    }
}
