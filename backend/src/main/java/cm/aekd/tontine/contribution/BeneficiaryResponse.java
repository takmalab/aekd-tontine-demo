package cm.aekd.tontine.contribution;

import java.util.UUID;

public record BeneficiaryResponse(UUID id, UUID memberId, String memberFullName) {
    public static BeneficiaryResponse from(ContributionBeneficiary beneficiary) {
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getMember().getId(),
                beneficiary.getMember().getFullName()
        );
    }
}
