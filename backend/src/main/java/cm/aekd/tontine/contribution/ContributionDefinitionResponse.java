package cm.aekd.tontine.contribution;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code hasPayments} : au moins un paiement (quel que soit son statut) existe
 * sur une période de la cotisation. Les champs financiers sont alors verrouillés
 * et la suppression est refusée (docs/decisions.md n° 35 et 36).
 */
public record ContributionDefinitionResponse(
        UUID id,
        String name,
        String description,
        BigDecimal amount,
        AmountMode amountMode,
        ContributionFrequency frequency,
        boolean mandatory,
        Visibility visibility,
        FundDestination fundDestination,
        ContributionStatus status,
        boolean hasPayments
) {
    public static ContributionDefinitionResponse from(ContributionDefinition definition, boolean hasPayments) {
        return new ContributionDefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getDescription(),
                definition.getAmount(),
                definition.getAmountMode(),
                definition.getFrequency(),
                definition.isMandatory(),
                definition.getVisibility(),
                definition.getFundDestination(),
                definition.getStatus(),
                hasPayments
        );
    }
}
