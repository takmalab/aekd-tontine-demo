package cm.aekd.tontine.contribution;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ContributionPeriodRequest(@NotNull UUID sessionId, LocalDate dueDate) {
}
