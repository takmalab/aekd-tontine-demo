package cm.aekd.tontine.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LoanRequest(
        @NotNull @Positive BigDecimal amount,
        @Positive int durationMonths,
        @NotBlank String reason
) {
}
