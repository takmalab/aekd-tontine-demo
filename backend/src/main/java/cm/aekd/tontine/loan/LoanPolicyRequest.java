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
        Integer maxActiveLoans,
        /** Avaliste obligatoire (decisions.md n° 52) ; absent = non. */
        Boolean guarantorRequired
) {
    public LoanPolicyRequest(String name, String description, BigDecimal minAmount, BigDecimal maxAmount,
                             Integer maxDurationMonths, BigDecimal interestRate, BigDecimal minSavingsRequired,
                             Integer minSeniorityMonths, Integer maxActiveLoans) {
        this(name, description, minAmount, maxAmount, maxDurationMonths, interestRate, minSavingsRequired,
                minSeniorityMonths, maxActiveLoans, null);
    }
}
