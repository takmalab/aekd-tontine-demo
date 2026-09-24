package cm.aekd.tontine.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SessionRequest(
        @NotBlank String label,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
