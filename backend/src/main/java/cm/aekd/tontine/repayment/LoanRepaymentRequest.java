package cm.aekd.tontine.repayment;

import cm.aekd.tontine.contribution.PaymentOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRepaymentRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paymentDate,
        @NotNull PaymentOperator operator,
        @NotBlank String reference
) {
}
