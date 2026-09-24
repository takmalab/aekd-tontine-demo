package cm.aekd.tontine.contribution;

import java.util.UUID;

/**
 * Abstraction de déclaration de paiement (CLAUDE.md §15). Le MVP n'a
 * qu'une implémentation (déclaration manuelle par le membre) ; l'interface
 * permet d'ajouter plus tard des implémentations branchées sur de vrais
 * opérateurs (MTN Mobile Money, Orange Money) sans changer les appelants.
 */
public interface PaymentService {

    ContributionTransactionResponse declare(UUID contributionDefinitionId, ContributionTransactionRequest request);
}
