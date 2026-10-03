package cm.aekd.tontine.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Situation d'un membre pour la page Membres (ADMIN/TRESORIER). Les compteurs
 * de cotisations portent sur les cotisations obligatoires de la séance actuelle
 * auxquelles le membre participe (même définition que MemberDashboardResponse).
 * L'encours de prêts est calculé comme LoanRepaymentService : montant approuvé
 * (principal) moins remboursements, sans intérêts (mode de calcul à définir).
 */
public record MemberSummaryResponse(
        UUID memberId,
        long mandatoryContributionsCount,
        long paidContributionsCount,
        long pendingContributionsCount,
        long lateContributionsCount,
        BigDecimal individualSavings,
        long activeLoansCount,
        BigDecimal outstandingLoanBalance,
        long activeSanctionsCount
) {
}
