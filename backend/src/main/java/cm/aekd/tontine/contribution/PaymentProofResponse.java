package cm.aekd.tontine.contribution;

import java.time.Instant;
import java.util.UUID;

public record PaymentProofResponse(
        UUID id,
        UUID contributionTransactionId,
        String originalFilename,
        String contentType,
        long fileSize,
        String uploadedByEmail,
        Instant uploadedAt
) {
    public static PaymentProofResponse from(PaymentProof proof) {
        return new PaymentProofResponse(
                proof.getId(),
                proof.getContributionTransaction().getId(),
                proof.getOriginalFilename(),
                proof.getContentType(),
                proof.getFileSize(),
                proof.getUploadedBy().getEmail(),
                proof.getUploadedAt()
        );
    }
}
