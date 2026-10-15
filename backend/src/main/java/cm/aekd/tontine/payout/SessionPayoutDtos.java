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
            @Size(max = 1000) String observation,
            /** Retenues pour rembourser des prêts (decisions.md n° 53), prises sur le montant. */
            List<@jakarta.validation.Valid DeductionLine> deductions
    ) {
        public PayoutRequest(UUID memberId, BigDecimal amount, LocalDate payoutDate, PaymentOperator operator,
                             String reference, String observation) {
            this(memberId, amount, payoutDate, operator, reference, observation, null);
        }
    }

    /** Retenue sur la cagnotte pour un prêt. */
    public record DeductionLine(
            @NotNull UUID loanId,
            @NotNull @Positive @Digits(integer = 14, fraction = 0) BigDecimal amount
    ) {
    }

    /**
     * Prêt pouvant faire l'objet d'une retenue sur la cagnotte d'un bénéficiaire :
     * le sien (BORROWER) ou un prêt qu'il garantit (GUARANTOR). {@code blockedReason}
     * explique pourquoi la retenue n'est pas possible (ex. l'emprunteur a encore de
     * l'épargne : l'emprunteur d'abord).
     */
    public record DeductionOption(
            UUID loanId,
            String borrowerFullName,
            String role,
            BigDecimal outstanding,
            boolean overdue,
            String blockedReason
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
            Instant createdAt,
            /** Retenue pour des prêts ; le bénéficiaire a reçu amount − deductedAmount. */
            BigDecimal deductedAmount
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
                    payout.getCreatedAt(),
                    payout.getDeductedAmount());
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
