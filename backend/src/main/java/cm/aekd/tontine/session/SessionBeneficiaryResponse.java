package cm.aekd.tontine.session;

import java.util.UUID;

/** Même forme que BeneficiaryResponse (cotisation), mais pour le bénéficiaire d'une séance. */
public record SessionBeneficiaryResponse(UUID id, UUID memberId, String memberFullName) {
    public static SessionBeneficiaryResponse from(SessionBeneficiary beneficiary) {
        return new SessionBeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getMember().getId(),
                beneficiary.getMember().getFullName()
        );
    }
}
