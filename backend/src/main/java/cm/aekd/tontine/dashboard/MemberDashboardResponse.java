package cm.aekd.tontine.dashboard;

import java.math.BigDecimal;

public record MemberDashboardResponse(
        SessionSummaryResponse currentSession,
        long mandatoryContributionsCount,
        long paidContributionsCount,
        long pendingContributionsCount,
        long lateContributionsCount,
        long optionalContributionsCount,
        BigDecimal individualSavings,
        long activeLoansCount,
        long pendingLoanRequestsCount,
        BigDecimal totalRepaid,
        long activeSanctionsCount
) {
}
