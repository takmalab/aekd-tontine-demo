package cm.aekd.tontine.fund;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Écran « Fonds » (CLAUDE.md §29, module cash ; decisions.md n° 50) : un solde
 * par compte, jamais mélangés (§17), et le journal des mouvements validés.
 */
public record FundOverviewResponse(
        List<AccountSummary> accounts,
        List<Movement> movements
) {

    /** Comptes de l'AEKD (decisions.md n° 50). */
    public enum Account { TONTINE_FUND, SAVINGS, SANCTIONS, CASH_FUND, RATION }

    public enum Direction { IN, OUT }

    public enum MovementType { CONTRIBUTION, PAYOUT, SAVINGS_DEPOSIT, LOAN_DISBURSED, LOAN_REPAYMENT }

    /** Détail chiffré d'un compte ; le code est traduit par l'interface. */
    public enum FigureCode {
        COLLECTED, REMITTED, TOTAL_SAVINGS, OUTSTANDING_LOAN_CAPITAL, LOAN_INTEREST_COLLECTED
    }

    public record Figure(FigureCode code, BigDecimal amount) {
    }

    public record AccountSummary(Account account, BigDecimal balance, List<Figure> figures) {

        public BigDecimal figure(FigureCode code) {
            return figures.stream().filter(f -> f.code() == code).map(Figure::amount).findFirst()
                    .orElse(BigDecimal.ZERO);
        }
    }

    public record Movement(
            LocalDate date,
            MovementType type,
            Account account,
            Direction direction,
            String label,
            String memberFullName,
            BigDecimal amount
    ) {
    }

    public AccountSummary account(Account account) {
        return accounts.stream().filter(a -> a.account() == account).findFirst().orElseThrow();
    }
}
