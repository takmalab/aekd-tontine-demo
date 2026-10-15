package cm.aekd.tontine.contribution;

public enum FundDestination {
    TONTINE_FUND,
    INDIVIDUAL_SAVINGS,
    /** Compte Ration : remis au bénéficiaire avec le fonds commun (decisions.md n° 50). */
    RATION,
    /** Compte Fond de caisse (decisions.md n° 50). */
    CASH_FUND
}
