package cm.aekd.tontine.notification;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionStatus;
import cm.aekd.tontine.contribution.ContributionTransactionRequest;
import cm.aekd.tontine.contribution.ContributionTransactionResponse;
import cm.aekd.tontine.contribution.ContributionTransactionService;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.contribution.PaymentService;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionContributionRequest;
import cm.aekd.tontine.session.SessionRepository;
import cm.aekd.tontine.session.SessionRequest;
import cm.aekd.tontine.session.SessionService;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ContributionReminderService reminderService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ContributionTransactionService transactionService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingsRepository settingsRepository;

    @Autowired
    private PushSubscriptionRepository subscriptionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ContributionDefinitionRepository definitionRepository;

    @Autowired
    private ContributionParticipantRepository participantRepository;

    @Autowired
    private ContributionPeriodRepository periodRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private Member treasurer;
    private Member admin;
    private Member member;

    @BeforeEach
    void setUp() {
        treasurer = createMember(RoleName.TRESORIER, "notiftreso");
        admin = createMember(RoleName.ADMIN, "notifadmin");
        member = createMember(RoleName.MEMBRE, "notifmembre");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staffIsNotifiedOfADeclaredPaymentAndTheMemberOfTheDecision() {
        ContributionPeriod period = periodFor(contribution("Cotisation 50 000", true), LocalDate.of(2026, 10, 3), null);

        loginAs(member, RoleName.MEMBRE);
        ContributionTransactionResponse declared = paymentService.declare(period.getContributionDefinition().getId(),
                new ContributionTransactionRequest(period.getId(), new BigDecimal(50000), PaymentOperator.MTN_MOMO,
                        "NOTIF-1", LocalDate.of(2026, 10, 3), null));

        assertThat(typesOf(treasurer)).containsExactly(NotificationType.PAYMENT_DECLARED);
        assertThat(typesOf(admin)).containsExactly(NotificationType.PAYMENT_DECLARED);
        // L'auteur de l'action n'est pas notifié.
        assertThat(typesOf(member)).isEmpty();

        loginAs(treasurer, RoleName.TRESORIER);
        transactionService.validate(declared.id());

        List<Notification> received = mine(member);
        assertThat(received).extracting(Notification::getType).containsExactly(NotificationType.PAYMENT_VALIDATED);
        assertThat(received.get(0).getMessage()).contains("Cotisation 50 000").contains("validé");
        assertThat(received.get(0).getLink()).isEqualTo("/my-contributions");
    }

    @Test
    void otherMembersAreInformedOfAValidatedPayment() {
        Member other = createMember(RoleName.MEMBRE, "notifautre");
        ContributionPeriod period = periodFor(contribution("Cotisation 50 000", true), LocalDate.of(2026, 10, 3), null);

        loginAs(member, RoleName.MEMBRE);
        ContributionTransactionResponse declared = paymentService.declare(period.getContributionDefinition().getId(),
                new ContributionTransactionRequest(period.getId(), new BigDecimal(50000), PaymentOperator.MTN_MOMO,
                        "NOTIF-2", LocalDate.of(2026, 10, 3), null));
        // Une simple déclaration n'informe pas les autres membres.
        assertThat(typesOf(other)).isEmpty();

        loginAs(treasurer, RoleName.TRESORIER);
        transactionService.validate(declared.id());

        // Cotisation PUBLIC : un membre non participant est informé, sans lien.
        List<Notification> received = mine(other);
        assertThat(received).extracting(Notification::getType).containsExactly(NotificationType.MEMBER_ACTIVITY);
        assertThat(received.get(0).getMessage()).contains("Test notifmembre").contains("Cotisation 50 000");
        assertThat(received.get(0).getLink()).isNull();
        // Le payeur reçoit sa propre notification, pas celle des autres membres.
        assertThat(typesOf(member)).containsExactly(NotificationType.PAYMENT_VALIDATED);
        // Le trésorier, auteur de la validation, n'est pas notifié.
        assertThat(typesOf(treasurer)).containsExactly(NotificationType.PAYMENT_DECLARED);
    }

    @Test
    void onlyParticipantsAreInformedOfActivityOnAPrivateContribution() {
        Member other = createMember(RoleName.MEMBRE, "notifautre");
        Member participant = createMember(RoleName.MEMBRE, "notifpart");
        ContributionDefinition privateContribution = contribution("Cotisation privée", true);
        privateContribution.setVisibility(Visibility.PRIVATE);
        participantRepository.save(new ContributionParticipant(privateContribution, participant));

        loginAs(treasurer, RoleName.TRESORIER);
        notificationService.notifyOtherMembers(member, privateContribution, "Paiement de cotisation", "Message");

        assertThat(typesOf(participant)).containsExactly(NotificationType.MEMBER_ACTIVITY);
        assertThat(typesOf(other)).isEmpty();
        assertThat(typesOf(admin)).isEmpty();
        assertThat(typesOf(member)).isEmpty();
    }

    @Test
    void staffAlreadyNotifiedIsNotInformedTwice() {
        Member other = createMember(RoleName.MEMBRE, "notifautre");

        loginAs(member, RoleName.MEMBRE);
        notificationService.notifyStaff(NotificationType.LOAN_REQUESTED, "Nouvelle demande de prêt", "Message",
                "/loans");
        notificationService.notifyOtherMembersExceptStaff(member, null, "Demande de prêt", "Message");

        assertThat(typesOf(treasurer)).containsExactly(NotificationType.LOAN_REQUESTED);
        assertThat(typesOf(admin)).containsExactly(NotificationType.LOAN_REQUESTED);
        assertThat(typesOf(other)).containsExactly(NotificationType.MEMBER_ACTIVITY);
        assertThat(typesOf(member)).isEmpty();
    }

    @Test
    void participantsAreNotifiedOfANewSessionOnceWithTheirContributionCount() {
        ContributionDefinition first = contribution("Cotisation 50 000", true);
        ContributionDefinition second = contribution("Ration", true);

        loginAs(treasurer, RoleName.TRESORIER);
        sessionService.create(new SessionRequest("Séance de novembre 2026", LocalDate.of(2026, 11, 7),
                "Chez Test", treasurer.getId(), List.of(treasurer.getId()), List.of(
                new SessionContributionRequest(first.getId(), null),
                new SessionContributionRequest(second.getId(), null))));

        List<Notification> received = mine(member);
        assertThat(received).extracting(Notification::getType).containsExactly(NotificationType.SESSION_CREATED);
        assertThat(received.get(0).getMessage()).contains("2 cotisations vous concernent");
        // Pas participant : pas de notification.
        assertThat(typesOf(admin)).isEmpty();
    }

    @Test
    void readStateIsPerUser() {
        loginAs(member, RoleName.MEMBRE);
        notificationService.notifyStaff(NotificationType.LOAN_REQUESTED, "Demande", "Message", "/loans");

        loginAs(treasurer, RoleName.TRESORIER);
        assertThat(notificationService.unreadCount()).isEqualTo(1);
        NotificationResponse first = notificationService.findMine(10).get(0);
        assertThat(notificationService.markRead(first.id()).read()).isTrue();
        assertThat(notificationService.unreadCount()).isZero();

        loginAs(admin, RoleName.ADMIN);
        assertThat(notificationService.unreadCount()).isEqualTo(1);
        notificationService.markAllRead();
        assertThat(notificationService.unreadCount()).isZero();
    }

    @Test
    void remindersTargetUnpaidMandatoryContributionsOnce() {
        LocalDate today = LocalDate.of(2026, 11, 1);
        periodFor(contribution("Cotisation 50 000", true), LocalDate.of(2026, 11, 7), today.plusDays(2));
        periodFor(contribution("Voir bébé", false), LocalDate.of(2026, 11, 7), today.plusDays(2));
        periodFor(contribution("Ration", true), LocalDate.of(2026, 11, 7), today.plusDays(10));

        // Sans réglage : aucun rappel.
        assertThat(reminderService.sendDueReminders(today)).isZero();

        NotificationSettings settings = settingsRepository.getSettings();
        settings.setReminderDaysBefore(3);
        settingsRepository.save(settings);

        assertThat(reminderService.sendDueReminders(today)).isEqualTo(1);
        List<Notification> received = mine(member);
        assertThat(received).extracting(Notification::getType).containsExactly(NotificationType.CONTRIBUTION_REMINDER);
        assertThat(received.get(0).getTitle()).isEqualTo("Rappel : Cotisation 50 000");

        // Une seule fois par membre et par période.
        assertThat(reminderService.sendDueReminders(today.plusDays(1))).isZero();
    }

    @Test
    void pushSubscriptionIsAttachedToTheCurrentAccount() {
        loginAs(member, RoleName.MEMBRE);
        notificationService.subscribe(new PushSubscriptionRequest("https://push.example/abc", "p256", "auth"), "Test");
        assertThat(subscriptionRepository.countByUserId(member.getUser().getId())).isEqualTo(1);

        // Même navigateur, autre compte : l'abonnement change de propriétaire.
        loginAs(treasurer, RoleName.TRESORIER);
        notificationService.subscribe(new PushSubscriptionRequest("https://push.example/abc", "p256", "auth"), "Test");
        assertThat(subscriptionRepository.countByUserId(member.getUser().getId())).isZero();
        assertThat(subscriptionRepository.countByUserId(treasurer.getUser().getId())).isEqualTo(1);

        notificationService.unsubscribe("https://push.example/abc");
        assertThat(subscriptionRepository.findByEndpoint("https://push.example/abc")).isEmpty();
        assertThat(notificationService.publicKey()).hasSize(87);
    }

    private ContributionDefinition contribution(String name, boolean mandatory) {
        ContributionDefinition definition = new ContributionDefinition(name, null, new BigDecimal(50000),
                AmountMode.FIXED, ContributionFrequency.MONTHLY, mandatory, Visibility.PUBLIC,
                FundDestination.TONTINE_FUND);
        definition.setStatus(ContributionStatus.ACTIVE);
        definition = definitionRepository.save(definition);
        participantRepository.save(new ContributionParticipant(definition, member));
        return definition;
    }

    private ContributionPeriod periodFor(ContributionDefinition definition, LocalDate sessionDate, LocalDate dueDate) {
        Session session = sessionRepository.save(new Session("Séance " + definition.getName(), sessionDate,
                "Douala", treasurer));
        return periodRepository.save(new ContributionPeriod(definition, session, dueDate));
    }

    private List<Notification> mine(Member owner) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(owner.getUser().getId(), Pageable.unpaged());
    }

    private List<NotificationType> typesOf(Member owner) {
        return mine(owner).stream().map(Notification::getType).toList();
    }

    private Member createMember(RoleName roleName, String emailPrefix) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(emailPrefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        return memberRepository.save(new Member(user, "Test " + emailPrefix, LocalDate.now()));
    }

    private void loginAs(Member who, RoleName roleName) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                who.getUser().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + roleName))));
    }
}
