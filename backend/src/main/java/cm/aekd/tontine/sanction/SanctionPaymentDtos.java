package cm.aekd.tontine.sanction;

import cm.aekd.tontine.contribution.PaymentOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Paiement des sanctions (decisions.md n° 50). */
public final class SanctionPaymentDtos {

    private SanctionPaymentDtos() {
    }

    public record DeclareRequest(
            @NotNull @Positive BigDecimal amount,
            @NotNull @PastOrPresent LocalDate paymentDate,
            @NotNull PaymentOperator operator,
            @NotBlank @Size(max = 150) String reference,
            @Size(max = 1000) String observation
    ) {
    }

    public record PaymentResponse(
            UUID id,
            UUID appliedSanctionId,
            UUID memberId,
            String memberFullName,
            String sanctionLabel,
            BigDecimal amount,
            LocalDate paymentDate,
            PaymentOperator operator,
            String reference,
            String observation,
            SanctionPaymentStatus status,
            boolean hasProof,
            String validatedByEmail,
            Instant validatedAt,
            Instant declaredAt
    ) {
        static PaymentResponse from(SanctionPayment p) {
            AppliedSanction s = p.getAppliedSanction();
            String label = s.getContributionPeriod().getContributionDefinition().getName() + " – "
                    + s.getContributionPeriod().getSession().getLabel();
            return new PaymentResponse(p.getId(), s.getId(), p.getMember().getId(), p.getMember().getFullName(),
                    label, p.getAmount(), p.getPaymentDate(), p.getOperator(), p.getReference(), p.getObservation(),
                    p.getStatus(), p.hasProof(),
                    p.getValidatedBy() != null ? p.getValidatedBy().getEmail() : null, p.getValidatedAt(),
                    p.getCreatedAt());
        }
    }
}
