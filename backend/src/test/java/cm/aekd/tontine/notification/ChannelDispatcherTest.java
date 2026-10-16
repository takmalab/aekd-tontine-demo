package cm.aekd.tontine.notification;

import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Notifications par e-mail et SMS selon les canaux activés (docs/decisions.md n° 58). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChannelDispatcherTest {

    @Autowired
    private ChannelDispatcher dispatcher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    private User user(String email, boolean verified, String phone) {
        User user = new User(email, "x");
        user.setPhone(phone);
        if (verified) {
            user.markEmailVerified();
        }
        return userRepository.save(user);
    }

    private UUID notify(User user, NotificationType type) {
        return notificationRepository.save(new Notification(user, type, "Paiement validé", "Votre paiement est validé.",
                "/my-contributions")).getId();
    }

    @Test
    void onlyEnabledChannelsAndReachableUsersAreUsed() {
        User verifiedWithPhone = user("chan.full@test.aekd.cm", true, "+237690000901");
        User unverifiedNoPhone = user("chan.none@test.aekd.cm", false, null);
        List<UUID> ids = List.of(notify(verifiedWithPhone, NotificationType.PAYMENT_VALIDATED),
                notify(unverifiedNoPhone, NotificationType.PAYMENT_VALIDATED),
                notify(verifiedWithPhone, NotificationType.LOAN_APPROVED));

        // Rien d'activé : uniquement la notification dans l'application.
        assertThat(dispatcher.prepare(ids)).isEmpty();

        NotificationChannel channel = new NotificationChannel(NotificationType.PAYMENT_VALIDATED);
        channel.update(true, true, null);
        channelRepository.save(channel);

        List<ChannelDispatcher.Delivery> deliveries = dispatcher.prepare(ids);
        // Le membre sans adresse vérifiée ni numéro ne reçoit rien ; LOAN_APPROVED n'est pas activé.
        assertThat(deliveries).singleElement().satisfies(d -> {
            assertThat(d.email()).isEqualTo("chan.full@test.aekd.cm");
            assertThat(d.byEmail()).isTrue();
            assertThat(d.bySms()).isTrue();
        });
    }
}
