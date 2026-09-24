package cm.aekd.tontine.contribution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ContributionDefinitionRequest(
        @NotBlank String name,
        String description,
        BigDecimal amount,
        @NotNull AmountMode amountMode,
        @NotNull ContributionFrequency frequency,
        boolean mandatory,
        @NotNull Visibility visibility,
        @NotNull FundDestination fundDestination
) {
}
