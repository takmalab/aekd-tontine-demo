package cm.aekd.tontine.notification;

import java.util.List;
import java.util.UUID;

/** Publié quand des notifications sont enregistrées ; le push part après validation de la transaction. */
public record NotificationsCreatedEvent(List<UUID> notificationIds) {
}
