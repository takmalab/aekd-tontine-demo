package cm.aekd.tontine.tournament;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Modification d'un tournoi actif. L'ordre de passage et la règle de date ne
 * changent pas : les dates annoncées aux membres restent stables. Pour les
 * changer, arrêter le tournoi et en créer un nouveau. Les cotisations par défaut
 * ne s'appliquent qu'aux séances générées ensuite.
 */
public record TournamentUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) @Max(365) Integer leadDays,
        List<@NotNull UUID> contributionDefinitionIds
) {
}
