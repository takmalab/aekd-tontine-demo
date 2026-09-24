package cm.aekd.tontine.contribution;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContributionTransactionResponse(
        UUID id,
        UUID memberId,
        String memberFullName,
        UUID contributionDefinitionId,
        String contributionDefinitionName,
        UUID contributionPeriodId,
        UUID sessionId,
        String sessionLabel,
        BigDecimal amount,
        PaymentOperator operator,
        String transactionReference,
        LocalDate paymentDate,
        String observation,
        ContributionTransactionStatus status,
        String validatedByEmail,
        Instant validatedAt
) {
    public static ContributionTransactionResponse from(ContributionTransaction transaction) {
        ContributionPeriod period = transaction.getContributionPeriod();
        ContributionDefinition definition = period.getContributionDefinition();
        return new ContributionTransactionResponse(
                transaction.getId(),
                transaction.getMember().getId(),
                transaction.getMember().getFullName(),
                definition.getId(),
                definition.getName(),
                period.getId(),
                period.getSession().getId(),
                period.getSession().getLabel(),
                transaction.getAmount(),
                transaction.getOperator(),
                transaction.getTransactionReference(),
                transaction.getPaymentDate(),
                transaction.getObservation(),
                transaction.getStatus(),
                transaction.getValidatedBy() != null ? transaction.getValidatedBy().getEmail() : null,
                transaction.getValidatedAt()
        );
    }
}
