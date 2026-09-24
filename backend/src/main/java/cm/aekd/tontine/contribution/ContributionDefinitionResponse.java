package cm.aekd.tontine.contribution;

import java.math.BigDecimal;
import java.util.UUID;

public record ContributionDefinitionResponse(
        UUID id,
        String name,
        String description,
        BigDecimal amount,
        AmountMode amountMode,
        ContributionFrequency frequency,
        boolean mandatory,
        Visibility visibility,
        FundDestination fundDestination,
        ContributionStatus status
) {
    public static ContributionDefinitionResponse from(ContributionDefinition definition) {
        return new ContributionDefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getDescription(),
                definition.getAmount(),
                definition.getAmountMode(),
                definition.getFrequency(),
                definition.isMandatory(),
                definition.getVisibility(),
                definition.getFundDestination(),
                definition.getStatus()
        );
    }
}
