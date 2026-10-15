package cm.aekd.tontine.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        UUID memberId,
        String memberFullName,
        UUID loanPolicyId,
        String loanPolicyName,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        int requestedDurationMonths,
        Integer approvedDurationMonths,
        /** Taux (%) par période : figé sur le prêt une fois approuvé, sinon celui de la politique. */
        BigDecimal interestRate,
        /** Période du taux en mois (decisions.md n° 53). */
        int interestPeriodMonths,
        BigDecimal interestAmount,
        /** Capital + intérêts à rembourser. */
        BigDecimal totalDue,
        /** Vrai avant l'approbation : intérêts et total calculés sur la demande, à titre indicatif. */
        boolean interestEstimated,
        String reason,
        LoanStatus status,
        Instant decisionDate,
        String decisionByEmail,
        String rejectionReason,
        List<LoanRuleEvaluationResponse> ruleEvaluations,
        Instant requestedAt,
        /** Avaliste (decisions.md n° 52) : absent si aucun n'a été désigné. */
        UUID guarantorMemberId,
        String guarantorFullName,
        GuarantorStatus guarantorStatus,
        Instant guarantorDecidedAt,
        /** Durée approuvée dépassée et prêt pas encore remboursé (decisions.md n° 53). */
        boolean overdue
) {
}
