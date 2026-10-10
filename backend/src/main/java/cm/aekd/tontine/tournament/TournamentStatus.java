package cm.aekd.tontine.tournament;

/**
 * ACTIVE : génère ses séances. STOPPED : arrêté par le trésorier ou l'administrateur.
 * COMPLETED : tous les membres de l'ordre de passage ont eu leur séance ; le
 * tournoi s'arrête et un nouveau tournoi doit être créé (décision n° 43).
 */
public enum TournamentStatus {
    ACTIVE,
    STOPPED,
    COMPLETED
}
