package cm.aekd.tontine.auth.emailcode;

import cm.aekd.tontine.auth.LoginResponse;
import cm.aekd.tontine.mail.EmailProperties;
import cm.aekd.tontine.mail.EmailSender;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import cm.aekd.tontine.user.UserStatus;
import cm.aekd.tontine.user.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Codes par e-mail (docs/decisions.md n° 55) : confirmation de l'adresse et connexion. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailCodeServiceTest {

    @TestConfiguration
    static class RecordingEmailConfig {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    /** Garde les e-mails au lieu de les envoyer ; le code termine l'objet. */
    static class RecordingEmailSender extends EmailSender {
        final List<String[]> sent = new ArrayList<>();

        RecordingEmailSender() {
            super(new EmailProperties(), RestClient.builder());
        }

        @Override
        public void send(String to, String subject, String text, String html) {
            sent.add(new String[]{to, subject.substring(subject.lastIndexOf(' ') + 1)});
        }

        String lastCode() {
            return sent.get(sent.size() - 1)[1];
        }
    }

    @Autowired
    private EmailCodeService service;

    @Autowired
    private RecordingEmailSender mail;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        mail.sent.clear();
    }

    private User createUser(String email, UserStatus status) {
        User user = new User(email, "x");
        user.addRole(roleRepository.findByName(RoleName.MEMBRE).orElseThrow());
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Test
    void addressIsConfirmedWithTheCodeThenUsedToSignIn() {
        User user = createUser("Code.Mail@test.aekd.cm", UserStatus.PENDING_VALIDATION);

        service.sendVerification("code.mail@test.aekd.cm");
        assertThat(mail.sent).hasSize(1);
        assertThatThrownBy(() -> service.verifyEmail("code.mail@test.aekd.cm", "000000x"))
                .hasMessageContaining("Code incorrect");
        service.verifyEmail("code.mail@test.aekd.cm", mail.lastCode());
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();

        // En attente de validation : page d'attente, pas de code de connexion.
        assertThatThrownBy(() -> service.requestLoginCode("code.mail@test.aekd.cm"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(403));

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        mail.sent.clear();
        // Le délai entre deux codes s'applique aussi entre confirmation et connexion.
        assertThatThrownBy(() -> service.requestLoginCode("code.mail@test.aekd.cm"))
                .hasMessageContaining("Patientez");
    }

    @Test
    void loginCodeIsOnlySentToAnActiveAccountWithAVerifiedAddress() {
        User unverified = createUser("unverified@test.aekd.cm", UserStatus.ACTIVE);
        service.requestLoginCode("unverified@test.aekd.cm");
        assertThat(mail.sent).isEmpty();

        unverified.markEmailVerified();
        userRepository.save(unverified);
        service.requestLoginCode("nobody@test.aekd.cm");
        assertThat(mail.sent).isEmpty();

        User verified = createUser("verified@test.aekd.cm", UserStatus.ACTIVE);
        verified.markEmailVerified();
        userRepository.save(verified);
        service.requestLoginCode("verified@test.aekd.cm");
        assertThat(mail.sent).hasSize(1);

        LoginResponse login = service.verifyLoginCode("verified@test.aekd.cm", mail.lastCode());
        assertThat(login.email()).isEqualTo("verified@test.aekd.cm");
        assertThat(login.refreshToken()).isNotBlank();
        // Un code ne sert qu'une fois.
        assertThatThrownBy(() -> service.verifyLoginCode("verified@test.aekd.cm", mail.lastCode()))
                .hasMessageContaining("invalide ou expiré");
    }

    @Test
    void connectedMemberVerifiesTheAddressFromTheProfile() {
        User user = createUser("profile.mail@test.aekd.cm", UserStatus.ACTIVE);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));

        service.sendVerificationToCurrentUser();
        service.verifyCurrentUser(mail.lastCode());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        assertThatThrownBy(() -> service.sendVerificationToCurrentUser()).hasMessageContaining("déjà vérifiée");
    }
}
