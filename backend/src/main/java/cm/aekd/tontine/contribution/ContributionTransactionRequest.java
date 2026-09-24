package cm.aekd.tontine.contribution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionTransactionRequest(
        @NotNull UUID contributionPeriodId,
        BigDecimal amount,
        @NotNull PaymentOperator operator,
        @NotBlank String transactionReference,
        @NotNull LocalDate paymentDate,
        String observation
) {
}
