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
        Boolean guarantorRequired,
        /** Période du taux en mois (n° 53) ; absente = mensuel. */
        Integer interestPeriodMonths,
        /** Prêts garantis à la fois par un même membre (n° 53) ; absent = sans limite. */
        Integer maxGuaranteesPerMember
) {
    public LoanPolicyRequest(String name, String description, BigDecimal minAmount, BigDecimal maxAmount,
                             Integer maxDurationMonths, BigDecimal interestRate, BigDecimal minSavingsRequired,
                             Integer minSeniorityMonths, Integer maxActiveLoans) {
        this(name, description, minAmount, maxAmount, maxDurationMonths, interestRate, minSavingsRequired,
                minSeniorityMonths, maxActiveLoans, null, null, null);
    }

    public LoanPolicyRequest(String name, String description, BigDecimal minAmount, BigDecimal maxAmount,
                             Integer maxDurationMonths, BigDecimal interestRate, BigDecimal minSavingsRequired,
                             Integer minSeniorityMonths, Integer maxActiveLoans, Boolean guarantorRequired) {
        this(name, description, minAmount, maxAmount, maxDurationMonths, interestRate, minSavingsRequired,
                minSeniorityMonths, maxActiveLoans, guarantorRequired, null, null);
    }
}
