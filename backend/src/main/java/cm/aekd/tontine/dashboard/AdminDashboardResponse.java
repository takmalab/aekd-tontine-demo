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
        BigDecimal tontineFundBalance,
        BigDecimal totalIndividualSavings,
        long loanRequestsPending,
        long loansInProgress,
        long activeSanctions
) {
}
