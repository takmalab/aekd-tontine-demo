package cm.aekd.tontine.notification;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import cm.aekd.tontine.user.UserStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Notifications dans l'application et Web Push (docs/decisions.md n° 44).
 * Les notifications sont enregistrées dans la transaction de l'opération qui les
 * déclenche ; le push n'est envoyé qu'après sa validation (PushDispatcher).
 * L'auteur d'une action n'est jamais notifié de sa propre action.
 */
@Service
@Transactional
public class NotificationService {

    private static final int MAX_LIST = 100;
    private static final Set<RoleName> STAFF_ROLES = Set.of(RoleName.ADMIN, RoleName.TRESORIER);

    private final NotificationRepository notificationRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final ContributionParticipantRepository participantRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final VapidKeyProvider vapidKeyProvider;

    public NotificationService(NotificationRepository notificationRepository,
                               PushSubscriptionRepository subscriptionRepository,
                               NotificationSettingsRepository settingsRepository,
                               UserRepository userRepository,
                               MemberRepository memberRepository,
                               ContributionParticipantRepository participantRepository,
                               CurrentUserProvider currentUserProvider,
                               ApplicationEventPublisher eventPublisher,
                               VapidKeyProvider vapidKeyProvider) {
        this.notificationRepository = notificationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.participantRepository = participantRepository;
        this.currentUserProvider = currentUserProvider;
        this.eventPublisher = eventPublisher;
        this.vapidKeyProvider = vapidKeyProvider;
    }

    // ---------- Envoi ----------

    /** Notifie le compte d'un membre. */
    public void notifyMember(Member member, NotificationType type, String title, String message, String link) {
        notifyUsers(List.of(member.getUser()), type, title, message, link);
    }

    public void notifyMembers(Collection<Member> members, NotificationType type, String title, String message,
                              String link) {
        notifyUsers(members.stream().map(Member::getUser).toList(), type, title, message, link);
    }

    /** Notifie le trésorier et l'administrateur (l'ADMIN a les droits du TRESORIER, décision n° 40). */
    public void notifyStaff(NotificationType type, String title, String message, String link) {
        notifyUsers(userRepository.findByRoleNamesAndStatus(STAFF_ROLES, UserStatus.ACTIVE), type, title, message,
                link);
    }

    /**
     * Informe les autres membres d'un mouvement de {@code subject} (docs/decisions.md n° 45) : tous les
     * membres actifs, sauf celui qui fait le mouvement. Pour une cotisation PRIVATE, seuls ses
     * participants sont informés (CLAUDE.md §11). {@code contribution} est null hors cotisation.
     */
    public void notifyOtherMembers(Member subject, ContributionDefinition contribution, String title,
                                   String message) {
        notifyOtherMembers(subject, contribution, title, message, false);
    }

    /**
     * Variante pour un mouvement dont le trésorier et l'administrateur sont déjà notifiés par
     * {@link #notifyStaff} (ex. demande de prêt) : ils ne reçoivent pas la notification en double.
     */
    public void notifyOtherMembersExceptStaff(Member subject, ContributionDefinition contribution, String title,
                                              String message) {
        notifyOtherMembers(subject, contribution, title, message, true);
    }

    private void notifyOtherMembers(Member subject, ContributionDefinition contribution, String title,
                                    String message, boolean excludeStaff) {
        List<Member> audience = memberRepository.findActiveByUserStatus(UserStatus.ACTIVE);
        if (contribution != null && contribution.getVisibility() == Visibility.PRIVATE) {
            Set<UUID> participantIds = participantRepository.findByContributionDefinitionId(contribution.getId())
                    .stream()
                    .map(participant -> participant.getMember().getId())
                    .collect(Collectors.toSet());
            audience = audience.stream().filter(m -> participantIds.contains(m.getId())).toList();
        }
        // Pas de lien : les pages de détail des autres membres sont réservées au personnel.
        notifyUsers(audience.stream()
                .filter(m -> !m.getId().equals(subject.getId()))
                .map(Member::getUser)
                .filter(user -> !excludeStaff || user.getRoles().stream()
                        .noneMatch(role -> STAFF_ROLES.contains(role.getName())))
                .toList(), NotificationType.MEMBER_ACTIVITY, title, message, null);
    }

    /** Notifie les administrateurs uniquement (ex. inscription à valider). */
    public void notifyAdmins(NotificationType type, String title, String message, String link) {
        notifyUsers(userRepository.findByRoleNamesAndStatus(Set.of(RoleName.ADMIN), UserStatus.ACTIVE),
                type, title, message, link);
    }

    private void notifyUsers(Collection<User> users, NotificationType type, String title, String message,
                             String link) {
        UUID actorId = currentUserProvider.currentUserId().orElse(null);
        Map<UUID, User> recipients = new LinkedHashMap<>();
        users.stream()
                .filter(Objects::nonNull)
                .filter(user -> !user.getId().equals(actorId))
                .forEach(user -> recipients.putIfAbsent(user.getId(), user));
        if (recipients.isEmpty()) {
            return;
        }
        List<UUID> ids = notificationRepository.saveAll(recipients.values().stream()
                        .map(user -> new Notification(user, type, title, message, link))
                        .toList())
                .stream()
                .map(Notification::getId)
                .toList();
        eventPublisher.publishEvent(new NotificationsCreatedEvent(ids));
    }

    // ---------- Consultation (utilisateur connecté) ----------

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMine(int limit) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(currentUserId(), PageRequest.of(0, Math.clamp(limit, 1, MAX_LIST)))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadAtIsNull(currentUserId());
    }

    public NotificationResponse markRead(UUID id) {
        Notification notification = notificationRepository.findByIdAndUserId(id, currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    public void markAllRead() {
        notificationRepository.markAllRead(currentUserId(), Instant.now());
    }

    // ---------- Web Push ----------

    @Transactional(readOnly = true)
    public String publicKey() {
        return vapidKeyProvider.keys().publicKey();
    }

    /** Enregistre (ou rattache au compte connecté) l'abonnement push de ce navigateur. */
    public void subscribe(PushSubscriptionRequest request, String userAgent) {
        User user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        subscriptionRepository.findByEndpoint(request.endpoint())
                .ifPresentOrElse(
                        existing -> existing.update(user, request.p256dh(), request.auth(), userAgent),
                        () -> subscriptionRepository.save(new PushSubscription(user, request.endpoint(),
                                request.p256dh(), request.auth(), userAgent)));
    }

    public void unsubscribe(String endpoint) {
        subscriptionRepository.deleteByEndpointAndUserId(endpoint, currentUserId());
    }

    /** Notification de test envoyée à soi-même, pour vérifier la réception sur cet appareil. */
    public void sendTest() {
        User user = userRepository.findById(currentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        Notification notification = notificationRepository.save(new Notification(user, NotificationType.TEST,
                "Notifications activées", "Vous recevrez ici les notifications de l'AEKD.", "/notifications"));
        eventPublisher.publishEvent(new NotificationsCreatedEvent(List.of(notification.getId())));
    }

    // ---------- Réglages ----------

    @Transactional(readOnly = true)
    public NotificationSettingsResponse settings() {
        return new NotificationSettingsResponse(settingsRepository.getSettings().getReminderDaysBefore());
    }

    public NotificationSettingsResponse updateSettings(NotificationSettingsRequest request) {
        NotificationSettings settings = settingsRepository.getSettings();
        settings.setReminderDaysBefore(request.reminderDaysBefore());
        settingsRepository.save(settings);
        return new NotificationSettingsResponse(settings.getReminderDaysBefore());
    }

    private UUID currentUserId() {
        return currentUserProvider.currentUserId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }
}
