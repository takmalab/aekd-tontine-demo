package cm.aekd.tontine.contribution;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Situation de paiement d'un participant pour une période donnée.
 * dueAmount et remainingAmount sont null pour une cotisation à montant libre.
 * Ne contient aucun détail de transaction (référence, opérateur, justificatif).
 */
public record ParticipantPaymentStatusResponse(
        UUID memberId,
        String memberFullName,
        ContributionPeriodStatus status,
        BigDecimal dueAmount,
        BigDecimal validatedAmount,
        BigDecimal pendingAmount,
        BigDecimal remainingAmount
) {
}
