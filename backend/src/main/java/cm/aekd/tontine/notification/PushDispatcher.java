package cm.aekd.tontine.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Envoie en Web Push les notifications enregistrées, uniquement après validation
 * de la transaction (une opération annulée ne notifie personne) et en tâche de
 * fond (un service push lent ne ralentit pas l'utilisateur). Un échec d'envoi
 * n'a pas d'effet sur la notification dans l'application.
 */
@Component
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    /** Message affiché par le service worker (public/push-sw.js). */
    record PushMessage(String title, String body, String url, String tag) {
    }

    private record Delivery(PushSubscription subscription, byte[] payload) {
    }

    private final NotificationRepository notificationRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSender pushSender;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PushDispatcher(NotificationRepository notificationRepository,
                          PushSubscriptionRepository subscriptionRepository,
                          PushSender pushSender,
                          ObjectMapper objectMapper,
                          TransactionTemplate transactionTemplate) {
        this.notificationRepository = notificationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.pushSender = pushSender;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationsCreated(NotificationsCreatedEvent event) {
        List<Delivery> deliveries = transactionTemplate.execute(status -> prepare(event.notificationIds()));
        if (deliveries == null) {
            return;
        }
        for (Delivery delivery : deliveries) {
            try {
                PushSender.Result result = pushSender.send(delivery.subscription(), delivery.payload());
                if (result.expired()) {
                    transactionTemplate.executeWithoutResult(
                            status -> subscriptionRepository.deleteByEndpoint(delivery.subscription().getEndpoint()));
                } else if (!result.delivered()) {
                    log.warn("Push refusé par le service ({}) pour un abonnement", result.status());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Envoi push impossible : {}", e.getMessage());
            }
        }
    }

    private List<Delivery> prepare(List<UUID> notificationIds) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        Map<UUID, List<PushSubscription>> byUser = subscriptionRepository
                .findByUserIdIn(notifications.stream().map(n -> n.getUser().getId()).distinct().toList())
                .stream()
                .collect(Collectors.groupingBy(s -> s.getUser().getId(), LinkedHashMap::new, Collectors.toList()));
        return notifications.stream()
                .flatMap(notification -> byUser.getOrDefault(notification.getUser().getId(), List.of()).stream()
                        .map(subscription -> new Delivery(subscription, payload(notification))))
                .toList();
    }

    private byte[] payload(Notification notification) {
        try {
            return objectMapper.writeValueAsString(new PushMessage(notification.getTitle(), notification.getMessage(),
                            notification.getLink(), notification.getId().toString()))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
