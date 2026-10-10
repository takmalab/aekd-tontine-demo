package cm.aekd.tontine.dashboard;

import java.math.BigDecimal;

public record AdminDashboardResponse(
        long memberCount,
        SessionSummaryResponse currentSession,
        long mandatoryContributionsExpected,
        long paymentsValidated,
        long paymentsPending,
        long lateContributions,
        BigDecimal amountCollected,
        /** Fonds commun restant : collecté − remis aux bénéficiaires (docs/decisions.md n° 46). */
        BigDecimal tontineFundBalance,
        BigDecimal tontineFundCollected,
        BigDecimal tontineFundRemitted,
        BigDecimal totalIndividualSavings,
        /** Argent d'épargne disponible pour prêter (docs/decisions.md n° 46). */
        BigDecimal availableForLending,
        long loanRequestsPending,
        long loansInProgress,
        long activeSanctions
) {
}
