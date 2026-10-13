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
 */
public final class LoanInterest {

    private static final BigDecimal FIVE = BigDecimal.valueOf(5);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private LoanInterest() {
    }

    /** Intérêts dus ; 0 si aucun taux n'est défini. */
    public static BigDecimal interest(BigDecimal capital, BigDecimal monthlyRatePercent, int months) {
        if (capital == null || monthlyRatePercent == null || monthlyRatePercent.signum() <= 0 || months <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal raw = capital.multiply(monthlyRatePercent)
                .multiply(BigDecimal.valueOf(months))
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        return roundUpToFive(raw);
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
