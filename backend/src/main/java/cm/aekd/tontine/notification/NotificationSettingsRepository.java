package cm.aekd.tontine.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Short> {

    default NotificationSettings getSettings() {
        return findById(NotificationSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Réglages des notifications absents (migration V17)"));
    }
}
