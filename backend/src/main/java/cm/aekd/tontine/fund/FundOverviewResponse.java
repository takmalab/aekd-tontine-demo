package cm.aekd.tontine.fund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Écran « Fonds » (CLAUDE.md §29, module cash) : soldes du fonds commun et de
 * l'épargne, séparés (§17), et journal des mouvements validés.
 */
public record FundOverviewResponse(
        BigDecimal tontineFundCollected,
        BigDecimal tontineFundRemitted,
        BigDecimal tontineFundBalance,
        BigDecimal totalIndividualSavings,
        BigDecimal outstandingLoanCapital,
        BigDecimal availableForLending,
        /** Part d'intérêts des remboursements validés (répartition proportionnelle, decisions.md n° 19). */
        BigDecimal loanInterestCollected,
        List<Movement> movements
) {

    /** Fonds concerné par un mouvement : jamais mélangés (CLAUDE.md §17). */
    public enum Fund { TONTINE_FUND, SAVINGS }

    public enum Direction { IN, OUT }

    public enum MovementType { CONTRIBUTION, PAYOUT, SAVINGS_DEPOSIT, LOAN_DISBURSED, LOAN_REPAYMENT }

    public record Movement(
            LocalDate date,
            MovementType type,
            Fund fund,
            Direction direction,
            String label,
            String memberFullName,
            BigDecimal amount
    ) {
    }
}
