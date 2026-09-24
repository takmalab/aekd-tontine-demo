package cm.aekd.tontine.sanction;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApplySanctionRequest(@NotNull UUID memberId, @NotNull UUID contributionPeriodId) {
}
