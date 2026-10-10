package cm.aekd.tontine.payout;

import cm.aekd.tontine.contribution.PaymentOperator;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTO de la remise de la cagnotte (docs/decisions.md n° 46). */
public final class SessionPayoutDtos {

    private SessionPayoutDtos() {
    }

    public record PayoutRequest(
            @NotNull UUID memberId,
            @NotNull @Positive @Digits(integer = 14, fraction = 0) BigDecimal amount,
            @NotNull @PastOrPresent LocalDate payoutDate,
            @NotNull PaymentOperator operator,
            @Size(max = 150) String reference,
            @Size(max = 1000) String observation
    ) {
    }

    public record PayoutResponse(
            UUID id,
            UUID sessionId,
            UUID memberId,
            String memberFullName,
            BigDecimal amount,
            LocalDate payoutDate,
            PaymentOperator operator,
            String reference,
            String observation,
            String recordedByEmail,
            Instant createdAt
    ) {
        static PayoutResponse from(SessionPayout payout) {
            return new PayoutResponse(
                    payout.getId(),
                    payout.getSession().getId(),
                    payout.getMember().getId(),
                    payout.getMember().getFullName(),
                    payout.getAmount(),
                    payout.getPayoutDate(),
                    payout.getOperator(),
                    payout.getReference(),
                    payout.getObservation(),
                    payout.getRecordedBy() != null ? payout.getRecordedBy().getEmail() : null,
                    payout.getCreatedAt());
        }
    }

    /** Une cotisation obligatoire du fonds commun de la séance et ce qu'elle a rapporté. */
    public record ContributionLine(
            UUID contributionDefinitionId,
            String name,
            BigDecimal collected
    ) {
    }

    /** Part d'un bénéficiaire : part égale, déjà remis, reste à lui remettre. */
    public record BeneficiaryShare(
            UUID memberId,
            String memberFullName,
            BigDecimal share,
            BigDecimal remitted,
            BigDecimal due
    ) {
    }

    /**
     * État de la cagnotte d'une séance.
     *
     * @param collected   paiements validés des cotisations obligatoires du fonds commun de la séance
     * @param remitted    total déjà remis aux bénéficiaires
     * @param remaining   collecté − remis
     * @param unallocated reliquat de la division en parts égales (arrondi au franc
     *                    inférieur), non attribué : DÉCISION À VALIDER
     */
    public record PayoutOverview(
            UUID sessionId,
            String sessionLabel,
            List<ContributionLine> contributions,
            BigDecimal collected,
            BigDecimal remitted,
            BigDecimal remaining,
            BigDecimal unallocated,
            List<BeneficiaryShare> beneficiaries,
            List<PayoutResponse> payouts
    ) {
    }
}
