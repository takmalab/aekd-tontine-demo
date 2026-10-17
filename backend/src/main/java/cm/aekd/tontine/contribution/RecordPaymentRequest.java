package cm.aekd.tontine.contribution;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Paiement enregistré par le trésorier pour un membre (décision n° 65), par exemple des
 * espèces remises à la séance. Sans montant, le reste dû est retenu ; la référence est
 * facultative (pas de référence pour des espèces).
 */
public record RecordPaymentRequest(
        @NotNull UUID memberId,
        @NotNull UUID contributionPeriodId,
        BigDecimal amount,
        @NotNull PaymentOperator operator,
        @Size(max = 150) String transactionReference,
        @NotNull LocalDate paymentDate,
        String observation
) {
}
