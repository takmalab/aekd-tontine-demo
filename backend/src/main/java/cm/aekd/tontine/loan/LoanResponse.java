package cm.aekd.tontine.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        UUID memberId,
        String memberFullName,
        UUID loanPolicyId,
        String loanPolicyName,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        int requestedDurationMonths,
        Integer approvedDurationMonths,
        String reason,
        LoanStatus status,
        Instant decisionDate,
        String decisionByEmail,
        String rejectionReason,
        List<LoanRuleEvaluationResponse> ruleEvaluations,
        Instant requestedAt
) {
}
