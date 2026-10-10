package cm.aekd.tontine.tournament;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Génère les séances dues des tournois en cours : au démarrage du serveur (rattrapage
 * après un arrêt) puis chaque jour. Désactivable par
 * {@code app.tournament.scheduler.enabled=false} (tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.tournament.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TournamentScheduler {

    private static final Logger log = LoggerFactory.getLogger(TournamentScheduler.class);

    private final TournamentService tournamentService;

    public TournamentScheduler(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${app.tournament.scheduler.cron:0 0 6 * * *}", zone = "Africa/Douala")
    public void daily() {
        run();
    }

    private void run() {
        LocalDate today = LocalDate.now(TournamentService.ZONE);
        for (UUID id : tournamentService.activeTournamentIds()) {
            try {
                tournamentService.generateDueSessions(id, today);
            } catch (RuntimeException e) {
                // Un tournoi en erreur ne doit pas bloquer les autres ; nouvel essai au prochain passage.
                log.warn("Génération des séances impossible pour le tournoi {} : {}", id, e.getMessage());
            }
        }
    }
}
