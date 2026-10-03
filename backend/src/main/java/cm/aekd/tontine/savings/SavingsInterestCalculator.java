package cm.aekd.tontine.savings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Intérêts de l'épargne (décision validée le 2026-10-03, docs/decisions.md n° 39) :
 * taux MENSUEL, intérêts SIMPLES, comptés par MOIS COMPLET écoulé depuis la
 * validation du versement. Les intérêts ne produisent pas d'intérêts.
 *
 * Le k-ième mois d'un versement validé le jour V se termine le jour V + k mois ;
 * il rapporte {@code montant × taux / 100} avec le taux en vigueur à cette date
 * de fin (taux le plus récent dont la date d'effet est antérieure ou égale).
 * Un mois terminé avant le premier taux fixé ne rapporte rien.
 */
public final class SavingsInterestCalculator {

    private SavingsInterestCalculator() {
    }

    /** Taux (en %) d'une date d'effet. Liste à fournir triée par date d'effet croissante. */
    public record Rate(LocalDate effectiveFrom, BigDecimal monthlyRate) {
    }

    /** Résultat pour un versement : mois complets écoulés et intérêts acquis (arrondis au franc). */
    public record Accrual(long completeMonths, BigDecimal interest) {
    }

    public static long completeMonths(LocalDate validatedOn, LocalDate today) {
        return validatedOn.isAfter(today) ? 0 : ChronoUnit.MONTHS.between(validatedOn, today);
    }

    public static Accrual accrue(BigDecimal amount, LocalDate validatedOn, LocalDate today, List<Rate> rates) {
        long months = completeMonths(validatedOn, today);
        BigDecimal interest = BigDecimal.ZERO;
        for (long k = 1; k <= months; k++) {
            BigDecimal rate = rateOn(validatedOn.plusMonths(k), rates);
            if (rate != null) {
                interest = interest.add(amount.multiply(rate));
            }
        }
        return new Accrual(months, interest.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP));
    }

    /** Taux en vigueur à une date, ou null si aucun taux n'était encore fixé. */
    public static BigDecimal rateOn(LocalDate date, List<Rate> rates) {
        BigDecimal current = null;
        for (Rate rate : rates) {
            if (rate.effectiveFrom().isAfter(date)) {
                break;
            }
            current = rate.monthlyRate();
        }
        return current;
    }
}
