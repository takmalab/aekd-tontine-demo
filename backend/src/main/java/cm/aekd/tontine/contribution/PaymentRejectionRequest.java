package cm.aekd.tontine.contribution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rejet d'un paiement : motif obligatoire, communiqué au membre (décision n° 66). */
public record PaymentRejectionRequest(@NotBlank @Size(max = 500) String reason) {
}
