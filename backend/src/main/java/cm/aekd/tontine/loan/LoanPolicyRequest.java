package cm.aekd.tontine.loan;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record LoanPolicyRequest(
        @NotBlank String name,
        String description,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Integer maxDurationMonths,
        BigDecimal interestRate,
        BigDecimal minSavingsRequired,
        Integer minSeniorityMonths,
        Integer maxActiveLoans
) {
}
