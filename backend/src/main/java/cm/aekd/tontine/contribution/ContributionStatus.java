package cm.aekd.tontine.contribution;

/**
 * Cycle de vie technique d'une cotisation (CLAUDE.md §8 "statut" et §31
 * étape "Activation"). DRAFT par défaut à la création, ACTIVE une fois
 * publiée, INACTIVE si désactivée. Ce n'est pas une règle métier : aucun
 * seuil ni montant n'est associé à ces états.
 */
public enum ContributionStatus {
    DRAFT,
    ACTIVE,
    INACTIVE
}
