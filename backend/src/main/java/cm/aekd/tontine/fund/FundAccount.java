package cm.aekd.tontine.fund;

import java.util.Set;

/** Comptes de l'AEKD (decisions.md n° 50), jamais mélangés (CLAUDE.md §17). */
public enum FundAccount {
    TONTINE_FUND, SAVINGS, SANCTIONS, CASH_FUND, RATION;

    /**
     * Comptes sur lesquels le trésorier peut enregistrer dépenses et virements
     * (n° 50). Le fonds commun et l'épargne sont protégés : ils ne bougent que
     * par les cotisations, les remises, les prêts et les remboursements.
     */
    public static final Set<FundAccount> OPERABLE = Set.of(SANCTIONS, CASH_FUND, RATION);

    public boolean isOperable() {
        return OPERABLE.contains(this);
    }
}
