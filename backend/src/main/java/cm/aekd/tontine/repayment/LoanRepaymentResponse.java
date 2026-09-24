package cm.aekd.tontine.repayment;

import cm.aekd.tontine.contribution.PaymentOperator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LoanRepaymentResponse(
        UUID id,
        UUID loanId,
        BigDecimal amount,
        LocalDate paymentDate,
        PaymentOperator operator,
        String reference,
        String recordedByEmail,
        Instant createdAt
) {
    public static LoanRepaymentResponse from(LoanRepayment repayment) {
        return new LoanRepaymentResponse(
                repayment.getId(),
                repayment.getLoan().getId(),
                repayment.getAmount(),
                repayment.getPaymentDate(),
                repayment.getOperator(),
                repayment.getReference(),
                repayment.getRecordedBy().getEmail(),
                repayment.getCreatedAt()
        );
    }
}
