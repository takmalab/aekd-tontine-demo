package cm.aekd.tontine.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Création d'une séance : un jour de réunion, un lieu, un membre récepteur
 * et un ou plusieurs bénéficiaires (identifiants de membres).
 */
public record SessionRequest(
        @NotBlank @Size(max = 100) String label,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 150) String location,
        @NotNull UUID hostMemberId,
        @NotEmpty List<@NotNull UUID> beneficiaryMemberIds
) {
}
