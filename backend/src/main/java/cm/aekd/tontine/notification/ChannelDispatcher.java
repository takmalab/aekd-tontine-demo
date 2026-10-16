package cm.aekd.tontine.notification;

import cm.aekd.tontine.mail.EmailSendException;
import cm.aekd.tontine.mail.EmailSender;
import cm.aekd.tontine.sms.SmsSendException;
import cm.aekd.tontine.sms.SmsSender;
import cm.aekd.tontine.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Envoie par e-mail et/ou SMS les notifications dont le type est activé par le
 * super administrateur (docs/decisions.md n° 58), en tâche de fond après la
 * validation de l'action (comme le push, PushDispatcher).
 * <ul>
 *   <li>e-mail : seulement vers une adresse vérifiée ;</li>
 *   <li>SMS : vers le numéro du compte, par un fournisseur qui permet le texte libre.</li>
 * </ul>
 * Un échec d'envoi est journalisé ; il n'annule jamais l'action ni la
 * notification dans l'application.
 */
@Component
public class ChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChannelDispatcher.class);
    private static final int SMS_MAX_LENGTH = 300;

    record Delivery(String email, String phone, boolean byEmail, boolean bySms, String title, String message,
                            String link, UUID notificationId) {
    }

    private final NotificationRepository notificationRepository;
    private final NotificationChannelRepository channelRepository;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final TransactionTemplate transactionTemplate;
    private final String publicUrl;

    public ChannelDispatcher(NotificationRepository notificationRepository,
                             NotificationChannelRepository channelRepository, EmailSender emailSender,
                             SmsSender smsSender, TransactionTemplate transactionTemplate,
                             @Value("${app.public-url:}") String publicUrl) {
        this.notificationRepository = notificationRepository;
        this.channelRepository = channelRepository;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.transactionTemplate = transactionTemplate;
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationsCreated(NotificationsCreatedEvent event) {
        List<Delivery> deliveries = transactionTemplate.execute(status -> prepare(event.notificationIds()));
        if (deliveries == null) {
            return;
        }
        for (Delivery d : deliveries) {
            if (d.byEmail()) {
                try {
                    emailSender.send(d.email(), "AEKD Tontine – " + d.title(), emailText(d), emailHtml(d));
                } catch (EmailSendException e) {
                    log.warn("Notification par e-mail non envoyée à {} : {}", d.email(), e.getMessage());
                }
            }
            if (d.bySms()) {
                try {
                    smsSender.sendText(d.phone(), smsText(d), "notif-" + d.notificationId());
                } catch (SmsSendException e) {
                    log.warn("Notification par SMS non envoyée à {} : {}", d.phone(), e.getMessage());
                }
            }
        }
    }

    List<Delivery> prepare(List<UUID> notificationIds) {
        Map<NotificationType, NotificationChannel> channels = channelRepository.findAll().stream()
                .collect(Collectors.toMap(NotificationChannel::getType, Function.identity()));
        if (channels.values().stream().noneMatch(c -> c.isEmailEnabled() || c.isSmsEnabled())) {
            return List.of();
        }
        List<Delivery> deliveries = new ArrayList<>();
        for (Notification n : notificationRepository.findAllById(notificationIds)) {
            NotificationChannel channel = channels.get(n.getType());
            if (channel == null) {
                continue;
            }
            User user = n.getUser();
            boolean byEmail = channel.isEmailEnabled() && user.isEmailVerified();
            boolean bySms = channel.isSmsEnabled() && user.getPhone() != null && !user.getPhone().isBlank();
            if (byEmail || bySms) {
                deliveries.add(new Delivery(user.getEmail(), user.getPhone(), byEmail, bySms, n.getTitle(),
                        n.getMessage(), n.getLink(), n.getId()));
            }
        }
        return deliveries;
    }

    private String absoluteLink(Delivery d) {
        return publicUrl.isEmpty() || d.link() == null ? null : publicUrl + d.link();
    }

    private String emailText(Delivery d) {
        String link = absoluteLink(d);
        return d.message() + (link != null ? "\n\nOuvrir dans l'application : " + link : "")
                + "\n\n— AEKD Tontine";
    }

    private String emailHtml(Delivery d) {
        String link = absoluteLink(d);
        return "<div style=\"font-family:Arial,sans-serif;color:#3a2a1f;max-width:520px\">"
                + "<h2 style=\"color:#e86e09;margin:0 0 12px\">" + HtmlUtils.htmlEscape(d.title()) + "</h2>"
                + "<p>" + HtmlUtils.htmlEscape(d.message()) + "</p>"
                + (link != null ? "<p><a href=\"" + HtmlUtils.htmlEscape(link)
                + "\" style=\"color:#e86e09\">Ouvrir dans l'application</a></p>" : "")
                + "<p style=\"color:#8a7a6f;font-size:13px\">AEKD Tontine</p></div>";
    }

    private static String smsText(Delivery d) {
        String text = "AEKD : " + d.title() + ". " + d.message();
        return text.length() <= SMS_MAX_LENGTH ? text : text.substring(0, SMS_MAX_LENGTH - 1) + "…";
    }
}
