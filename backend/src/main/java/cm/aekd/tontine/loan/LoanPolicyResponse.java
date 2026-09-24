package cm.aekd.tontine.loan;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanPolicyResponse(
        UUID id,
        String name,
        String description,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer maxDurationMonths,
        BigDecimal interestRate,
        BigDecimal minSavingsRequired,
        Integer minSeniorityMonths,
        Integer maxActiveLoans,
        boolean active
) {
    public static LoanPolicyResponse from(LoanPolicy policy) {
        return new LoanPolicyResponse(
                policy.getId(), policy.getName(), policy.getDescription(), policy.getMinAmount(),
                policy.getMaxAmount(), policy.getMaxDurationMonths(), policy.getInterestRate(),
                policy.getMinSavingsRequired(), policy.getMinSeniorityMonths(), policy.getMaxActiveLoans(),
                policy.isActive()
        );
    }
}
