package cm.aekd.tontine.contribution;

import java.util.UUID;

public record ParticipantResponse(UUID id, UUID memberId, String memberFullName) {
    public static ParticipantResponse from(ContributionParticipant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getMember().getId(),
                participant.getMember().getFullName()
        );
    }
}
