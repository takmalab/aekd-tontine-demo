package cm.aekd.tontine.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, NotificationType> {
}
