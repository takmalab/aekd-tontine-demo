package cm.aekd.tontine.sanction;

import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionPeriod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppliedSanctionResponse(
        UUID id,
        UUID sanctionRuleId,
        SanctionType type,
        UUID memberId,
        String memberFullName,
        UUID contributionDefinitionId,
        String contributionDefinitionName,
        UUID contributionPeriodId,
        UUID sessionId,
        String sessionLabel,
        BigDecimal amount,
        String description,
        AppliedSanctionStatus status,
        String appliedByEmail,
        Instant appliedAt,
        /** Paiements validés (decisions.md n° 50) ; 0 pour une sanction en nature. */
        BigDecimal paidAmount,
        /** Paiements déclarés, en attente de validation. */
        BigDecimal pendingAmount
) {
    public static AppliedSanctionResponse from(AppliedSanction sanction, BigDecimal paidAmount,
                                               BigDecimal pendingAmount) {
        ContributionPeriod period = sanction.getContributionPeriod();
        ContributionDefinition definition = period.getContributionDefinition();
        return new AppliedSanctionResponse(
                sanction.getId(),
                sanction.getSanctionRule().getId(),
                sanction.getSanctionRule().getType(),
                sanction.getMember().getId(),
                sanction.getMember().getFullName(),
                definition.getId(),
                definition.getName(),
                period.getId(),
                period.getSession().getId(),
                period.getSession().getLabel(),
                sanction.getAmount(),
                sanction.getDescription(),
                sanction.getStatus(),
                sanction.getAppliedBy() != null ? sanction.getAppliedBy().getEmail() : null,
                sanction.getCreatedAt(),
                paidAmount,
                pendingAmount
        );
    }
}
