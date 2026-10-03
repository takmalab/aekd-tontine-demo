package cm.aekd.tontine.repayment;

import cm.aekd.tontine.contribution.PaymentOperator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code memberFullName} sert à la liste des remboursements à valider ;
 * {@code validatedByEmail} / {@code validatedAt} : auteur et date de la
 * décision (validation ou rejet).
 */
public record LoanRepaymentResponse(
        UUID id,
        UUID loanId,
        UUID memberId,
        String memberFullName,
        BigDecimal amount,
        LocalDate paymentDate,
        PaymentOperator operator,
        String reference,
        String recordedByEmail,
        Instant createdAt,
        LoanRepaymentStatus status,
        String validatedByEmail,
        Instant validatedAt,
        boolean hasProof
) {
    public static LoanRepaymentResponse from(LoanRepayment repayment) {
        return new LoanRepaymentResponse(
                repayment.getId(),
                repayment.getLoan().getId(),
                repayment.getLoan().getMember().getId(),
                repayment.getLoan().getMember().getFullName(),
                repayment.getAmount(),
                repayment.getPaymentDate(),
                repayment.getOperator(),
                repayment.getReference(),
                repayment.getRecordedBy() != null ? repayment.getRecordedBy().getEmail() : null,
                repayment.getCreatedAt(),
                repayment.getStatus(),
                repayment.getValidatedBy() != null ? repayment.getValidatedBy().getEmail() : null,
                repayment.getValidatedAt(),
                repayment.hasProof()
        );
    }
}
