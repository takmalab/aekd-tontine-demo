package cm.aekd.tontine.session;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cotisation existante à rattacher à une séance (création d'une ContributionPeriod).
 * La date limite reste facultative : le délai de paiement est une décision à
 * valider (docs/decisions.md n° 6), aucune valeur n'est déduite automatiquement.
 */
public record SessionContributionRequest(
        @NotNull UUID contributionDefinitionId,
        LocalDate dueDate
) {
}
