package cm.aekd.tontine.sanction;

import java.math.BigDecimal;
import java.util.UUID;

public record SanctionRuleResponse(
        UUID id,
        UUID contributionDefinitionId,
        String contributionDefinitionName,
        SanctionType type,
        int lateDaysThreshold,
        BigDecimal monetaryAmount,
        String description,
        boolean active
) {
    public static SanctionRuleResponse from(SanctionRule rule) {
        return new SanctionRuleResponse(
                rule.getId(),
                rule.getContributionDefinition().getId(),
                rule.getContributionDefinition().getName(),
                rule.getType(),
                rule.getLateDaysThreshold(),
                rule.getMonetaryAmount(),
                rule.getDescription(),
                rule.isActive()
        );
    }
}
