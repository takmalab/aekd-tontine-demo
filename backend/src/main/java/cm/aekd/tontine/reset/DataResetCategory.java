package cm.aekd.tontine.reset;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Catégories de données effaçables par le super administrateur (docs/decisions.md n° 63).
 * Chaque catégorie entraîne celles qui en dépendent ({@link #implies()}), pour que les
 * comptes restent cohérents : effacer les cotisations efface aussi leurs paiements, les
 * sanctions, les remises de cagnotte et les opérations des comptes qu'elles alimentaient.
 * L'ordre de déclaration est l'ordre de suppression (dépendances d'abord).
 */
public enum DataResetCategory {

    NOTIFICATIONS("Notifications et rappels envoyés",
            List.of("DELETE FROM notification", "DELETE FROM contribution_reminder")),
    TOURNAMENTS("Tournois (ordres de passage)",
            List.of("DELETE FROM tournament")),
    PAYOUTS("Remises de cagnotte aux bénéficiaires",
            List.of("DELETE FROM session_payout")),
    ACCOUNT_OPERATIONS("Dépenses et virements des comptes Sanctions, Fond de caisse et Ration",
            List.of("DELETE FROM account_operation")),
    SANCTIONS("Sanctions : règles, sanctions appliquées et leurs paiements",
            List.of("DELETE FROM sanction_rule")),
    LOANS("Prêts, avals et remboursements",
            List.of("DELETE FROM loan")),
    LOAN_POLICIES("Politiques de prêt",
            List.of("DELETE FROM loan_policy")),
    SAVINGS("Épargne : versements libres et taux d'intérêt",
            List.of("DELETE FROM savings_deposit", "DELETE FROM savings_interest_rate")),
    CONTRIBUTIONS("Cotisations : participants, bénéficiaires, paiements et justificatifs",
            List.of("DELETE FROM contribution_definition")),
    SESSIONS("Séances : bénéficiaires, rattachements des cotisations et paiements de ces séances",
            List.of("DELETE FROM session")),
    /** Tous les comptes sauf les super administrateurs et le compte qui réinitialise. */
    MEMBERS("Comptes et membres (sauf super administrateurs), avec tout leur historique",
            List.of());

    private final String label;
    private final List<String> statements;

    DataResetCategory(String label, List<String> statements) {
        this.label = label;
        this.statements = statements;
    }

    public String label() {
        return label;
    }

    List<String> statements() {
        return statements;
    }

    /** Catégories entraînées directement par celle-ci. */
    public Set<DataResetCategory> implies() {
        return switch (this) {
            // Les cotisations portent les règles de sanction ; leurs paiements alimentent le fonds
            // commun (remises) et les comptes Ration/Sanctions (dépenses, virements).
            case CONTRIBUTIONS -> EnumSet.of(SANCTIONS, PAYOUTS, ACCOUNT_OPERATIONS);
            // Une séance avec une remise ne peut pas être supprimée ; ses paiements disparaissent.
            case SESSIONS -> EnumSet.of(PAYOUTS, ACCOUNT_OPERATIONS);
            // Les paiements de sanctions alimentent le compte Sanctions.
            case SANCTIONS -> EnumSet.of(ACCOUNT_OPERATIONS);
            // Un prêt référence sa politique.
            case LOAN_POLICIES -> EnumSet.of(LOANS);
            default -> EnumSet.noneOf(DataResetCategory.class);
        };
    }

    /** Fermeture transitive des dépendances. */
    public static Set<DataResetCategory> withDependencies(Set<DataResetCategory> selected) {
        Set<DataResetCategory> result = EnumSet.noneOf(DataResetCategory.class);
        List<DataResetCategory> pending = new java.util.ArrayList<>(selected);
        while (!pending.isEmpty()) {
            DataResetCategory category = pending.remove(pending.size() - 1);
            if (result.add(category)) {
                pending.addAll(category.implies());
            }
        }
        return result;
    }
}
