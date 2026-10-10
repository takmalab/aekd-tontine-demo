package cm.aekd.tontine.tournament;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Création d'un tournoi. {@code memberIds} est l'ordre de passage : le premier
 * membre reçoit la séance du mois de départ, le suivant celle du mois suivant…
 */
public record TournamentRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull YearMonth startMonth,
        @NotNull Integer weekOfMonth,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @Min(0) @Max(365) Integer leadDays,
        @NotEmpty List<@NotNull UUID> memberIds,
        List<@NotNull UUID> contributionDefinitionIds
) {
}
