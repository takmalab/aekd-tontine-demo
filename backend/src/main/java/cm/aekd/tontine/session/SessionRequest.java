package cm.aekd.tontine.session;

import jakarta.validation.Valid;
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
 *
 * {@code contributions} : cotisations existantes rattachées à la séance
 * (docs/decisions.md n° 42). En création, null équivaut à une liste vide ; en
 * modification, null laisse les rattachements inchangés et une liste remplace
 * l'ensemble des rattachements.
 */
public record SessionRequest(
        @NotBlank @Size(max = 100) String label,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 150) String location,
        @NotNull UUID hostMemberId,
        @NotEmpty List<@NotNull UUID> beneficiaryMemberIds,
        List<@NotNull @Valid SessionContributionRequest> contributions
) {
    /** Séance sans changement de ses cotisations rattachées. */
    public SessionRequest(String label, LocalDate date, String location, UUID hostMemberId,
                          List<UUID> beneficiaryMemberIds) {
        this(label, date, location, hostMemberId, beneficiaryMemberIds, null);
    }
}
