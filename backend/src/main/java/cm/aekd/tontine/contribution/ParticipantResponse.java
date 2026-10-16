package cm.aekd.tontine.contribution;

import java.time.LocalDate;
import java.util.UUID;

/** {@code effectiveFrom} : décision n° 60 — null si le membre compte pour toutes les séances. */
public record ParticipantResponse(UUID id, UUID memberId, String memberFullName, LocalDate effectiveFrom) {
    public static ParticipantResponse from(ContributionParticipant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getMember().getId(),
                participant.getMember().getFullName(),
                participant.getEffectiveFrom()
        );
    }
}
