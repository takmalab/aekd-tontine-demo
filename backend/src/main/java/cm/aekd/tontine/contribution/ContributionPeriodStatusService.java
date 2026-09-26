package cm.aekd.tontine.contribution;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Détermine la situation d'un membre pour une période (CLAUDE.md §13).
 * "En retard" suit la définition littérale du §25 (date limite dépassée,
 * aucun paiement validé) sans délai de grâce supplémentaire — le seuil
 * configurable des règles de sanction (§18-19) est un concept distinct.
 *
 * <p>Cotisation à montant fixe : les paiements partiels sont autorisés
 * (décision du porteur du projet). Le statut dépend de la somme validée :
 * <ul>
 *   <li>PAID : somme validée ≥ montant de la cotisation ;</li>
 *   <li>PENDING : un paiement est en attente de validation ;</li>
 *   <li>PARTIAL : 0 &lt; somme validée &lt; montant, rien en attente ;</li>
 *   <li>LATE : rien de validé ni en attente, échéance dépassée ;</li>
 *   <li>NOT_PAID : sinon.</li>
 * </ul>
 * Décision validée par le porteur du projet (2026-09-25) : un paiement partiel
 * dont l'échéance est dépassée est affiché PARTIAL (et non LATE).
 *
 * <p>Cotisation à montant libre : logique inchangée (PAID si un paiement est
 * validé, PENDING si un paiement est en attente, puis LATE/NOT_PAID).
 */
@Service
public class ContributionPeriodStatusService {

    private final ContributionTransactionRepository transactionRepository;

    public ContributionPeriodStatusService(ContributionTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public ContributionPeriodStatus resolve(ContributionPeriod period, UUID memberId) {
        ContributionDefinition definition = period.getContributionDefinition();
        if (isFixed(definition)) {
            BigDecimal validated = validatedAmount(period, memberId);
            if (validated.compareTo(definition.getAmount()) >= 0) {
                return ContributionPeriodStatus.PAID;
            }
            if (hasPending(period, memberId)) {
                return ContributionPeriodStatus.PENDING;
            }
            if (validated.signum() > 0) {
                return ContributionPeriodStatus.PARTIAL;
            }
            return isOverdue(period) ? ContributionPeriodStatus.LATE : ContributionPeriodStatus.NOT_PAID;
        }

        if (transactionRepository.existsByMemberIdAndContributionPeriodIdAndStatus(
                memberId, period.getId(), ContributionTransactionStatus.VALIDATED)) {
            return ContributionPeriodStatus.PAID;
        }
        if (hasPending(period, memberId)) {
            return ContributionPeriodStatus.PENDING;
        }
        return isOverdue(period) ? ContributionPeriodStatus.LATE : ContributionPeriodStatus.NOT_PAID;
    }

    /** Somme des paiements VALIDÉS du membre pour la période. */
    public BigDecimal validatedAmount(ContributionPeriod period, UUID memberId) {
        return transactionRepository.sumAmountByMemberAndPeriodAndStatus(
                memberId, period.getId(), ContributionTransactionStatus.VALIDATED);
    }

    /** Montant fixe restant dû (jamais négatif) ; null pour une cotisation à montant libre. */
    public BigDecimal remainingAmount(ContributionPeriod period, UUID memberId) {
        ContributionDefinition definition = period.getContributionDefinition();
        if (!isFixed(definition)) {
            return null;
        }
        return definition.getAmount().subtract(validatedAmount(period, memberId)).max(BigDecimal.ZERO);
    }

    public boolean hasPending(ContributionPeriod period, UUID memberId) {
        return transactionRepository.existsByMemberIdAndContributionPeriodIdAndStatus(
                memberId, period.getId(), ContributionTransactionStatus.PENDING);
    }

    /**
     * Période entièrement réglée : somme validée ≥ montant (montant fixe), ou au
     * moins un paiement validé (montant libre, comportement historique).
     */
    public boolean isFullyPaid(ContributionPeriod period, UUID memberId) {
        ContributionDefinition definition = period.getContributionDefinition();
        if (isFixed(definition)) {
            return validatedAmount(period, memberId).compareTo(definition.getAmount()) >= 0;
        }
        return transactionRepository.existsByMemberIdAndContributionPeriodIdAndStatus(
                memberId, period.getId(), ContributionTransactionStatus.VALIDATED);
    }

    private static boolean isFixed(ContributionDefinition definition) {
        return definition.getAmountMode() == AmountMode.FIXED && definition.getAmount() != null;
    }

    private static boolean isOverdue(ContributionPeriod period) {
        return period.getDueDate() != null && LocalDate.now().isAfter(period.getDueDate());
    }
}
