package cm.aekd.tontine.sanction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SanctionRuleRequest(
        @NotNull UUID contributionDefinitionId,
        @NotNull SanctionType type,
        @Min(0) int lateDaysThreshold,
        BigDecimal monetaryAmount,
        String description
) {
}
