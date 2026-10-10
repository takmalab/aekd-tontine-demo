package cm.aekd.tontine.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Rappels de cotisations : au démarrage du serveur puis chaque jour à 7 h (Douala).
 * Désactivable par {@code app.notification.reminders.enabled=false} (tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notification.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    private final ContributionReminderService reminderService;

    public NotificationScheduler(ContributionReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(cron = "${app.notification.reminders.cron:0 0 7 * * *}", zone = "Africa/Douala")
    public void daily() {
        run();
    }

    private void run() {
        try {
            int sent = reminderService.sendDueReminders(LocalDate.now(ZONE));
            if (sent > 0) {
                log.info("{} rappel(s) de cotisation envoyé(s)", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Envoi des rappels de cotisation impossible : {}", e.getMessage());
        }
    }
}
