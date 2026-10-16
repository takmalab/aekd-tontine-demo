package cm.aekd.tontine.notification;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.mail.EmailSender;
import cm.aekd.tontine.sms.SmsProvider;
import cm.aekd.tontine.sms.SmsRouter;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * « Notifications e-mail et SMS » : super administrateur (docs/decisions.md n° 58).
 * Pour chaque type de notification, l'e-mail et le SMS s'activent séparément.
 */
@RestController
@RequestMapping("/api/settings/notifications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Transactional
public class NotificationChannelController {

    private static final UUID AUDIT_ENTITY_ID = new UUID(0, 4);

    private final NotificationChannelRepository repository;
    private final EmailSender emailSender;
    private final SmsRouter smsRouter;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public NotificationChannelController(NotificationChannelRepository repository, EmailSender emailSender,
                                         SmsRouter smsRouter, AuditLogService auditLogService,
                                         CurrentUserProvider currentUserProvider, UserRepository userRepository) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.smsRouter = smsRouter;
        this.auditLogService = auditLogService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    public record ChannelRow(NotificationType type, boolean emailEnabled, boolean smsEnabled) {
    }

    /**
     * @param emailLive les e-mails partent réellement (sinon simulation)
     * @param smsLive   le fournisseur SMS actif envoie réellement des notifications
     * @param smsStatus explication de l'état des SMS
     */
    public record ChannelsResponse(List<ChannelRow> channels, boolean emailLive, boolean smsLive, String smsStatus) {
    }

    public record ChannelsRequest(@NotNull List<@Valid ChannelRow> channels) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ChannelsResponse get() {
        Map<NotificationType, NotificationChannel> saved = repository.findAll().stream()
                .collect(Collectors.toMap(NotificationChannel::getType, Function.identity()));
        List<ChannelRow> rows = Arrays.stream(NotificationType.values())
                .filter(type -> type != NotificationType.TEST)
                .map(type -> {
                    NotificationChannel c = saved.get(type);
                    return new ChannelRow(type, c != null && c.isEmailEnabled(), c != null && c.isSmsEnabled());
                })
                .toList();
        SmsProvider active = smsRouter.activeProvider();
        boolean smsLive = !active.isSimulation() && active.supportsText();
        String smsStatus = active.isSimulation()
                ? "Les SMS sont en simulation (Paramètres SMS) : rien ne part réellement."
                : active.supportsText()
                ? "Les notifications SMS partent par " + active.label() + "."
                : active.label() + " n'envoie que des modèles validés : configurez un modèle de notification"
                + " (SCMCHOST_NOTIFY_TEMPLATE_ID) ou utilisez un autre fournisseur.";
        return new ChannelsResponse(rows, emailSender.isLive(), smsLive, smsStatus);
    }

    @PutMapping
    public ChannelsResponse update(@Valid @RequestBody ChannelsRequest request) {
        User by = currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null);
        long email = 0;
        long sms = 0;
        for (ChannelRow row : request.channels()) {
            if (row.type() == null || row.type() == NotificationType.TEST) {
                continue;
            }
            NotificationChannel channel = repository.findById(row.type()).orElseGet(() -> new NotificationChannel(row.type()));
            channel.update(row.emailEnabled(), row.smsEnabled(), by);
            repository.save(channel);
            email += row.emailEnabled() ? 1 : 0;
            sms += row.smsEnabled() ? 1 : 0;
        }
        auditLogService.record(AuditAction.NOTIFICATION_CHANNELS_UPDATED, "NotificationChannel", AUDIT_ENTITY_ID,
                "Notifications : " + email + " type(s) par e-mail, " + sms + " type(s) par SMS");
        return get();
    }
}
