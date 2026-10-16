package cm.aekd.tontine.sanction;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code used} : des sanctions ont été appliquées avec la règle (type figé, suppression refusée, n° 61). */
public record SanctionRuleResponse(
        UUID id,
        UUID contributionDefinitionId,
        String contributionDefinitionName,
        SanctionType type,
        int lateDaysThreshold,
        BigDecimal monetaryAmount,
        String description,
        boolean active,
        boolean used
) {
    public static SanctionRuleResponse from(SanctionRule rule, boolean used) {
        return new SanctionRuleResponse(
                rule.getId(),
                rule.getContributionDefinition().getId(),
                rule.getContributionDefinition().getName(),
                rule.getType(),
                rule.getLateDaysThreshold(),
                rule.getMonetaryAmount(),
                rule.getDescription(),
                rule.isActive(),
                used
        );
    }
}
