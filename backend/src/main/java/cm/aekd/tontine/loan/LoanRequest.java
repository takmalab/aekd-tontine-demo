package cm.aekd.tontine.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanRequest(
        @NotNull @Positive BigDecimal amount,
        @Positive int durationMonths,
        @NotBlank String reason,
        /** Membre avaliste (decisions.md n° 52) ; requis si la politique l'exige. */
        UUID guarantorMemberId
) {
    public LoanRequest(BigDecimal amount, int durationMonths, String reason) {
        this(amount, durationMonths, reason, null);
    }
}
