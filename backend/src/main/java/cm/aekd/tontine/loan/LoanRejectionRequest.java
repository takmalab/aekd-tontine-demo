package cm.aekd.tontine.loan;

import jakarta.validation.constraints.NotBlank;

public record LoanRejectionRequest(@NotBlank String rejectionReason) {
}
