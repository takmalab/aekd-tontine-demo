package cm.aekd.tontine.sanction;

import java.util.UUID;

public record SanctionCandidateResponse(
        UUID memberId,
        String memberFullName,
        UUID contributionPeriodId,
        UUID sessionId,
        String sessionLabel
) {
}
