package cm.aekd.tontine.contribution;

import java.time.LocalDate;
import java.util.UUID;

public record ContributionPeriodResponse(
        UUID id,
        UUID contributionDefinitionId,
        UUID sessionId,
        String sessionLabel,
        LocalDate dueDate
) {
    public static ContributionPeriodResponse from(ContributionPeriod period) {
        return new ContributionPeriodResponse(
                period.getId(),
                period.getContributionDefinition().getId(),
                period.getSession().getId(),
                period.getSession().getLabel(),
                period.getDueDate()
        );
    }
}
