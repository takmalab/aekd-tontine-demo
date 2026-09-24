package cm.aekd.tontine.loan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LoanApprovalRequest(
        @NotNull @Positive BigDecimal approvedAmount,
        @NotNull @Positive Integer approvedDurationMonths
) {
}
