package cm.aekd.tontine.auth;

import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import cm.aekd.tontine.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginSucceedsWithValidCredentials() {
        Role memberRole = roleRepository.findByName(RoleName.MEMBRE).orElseThrow();
        User user = new User("test.login@aekd.cm", passwordEncoder.encode("Secret@123"));
        user.addRole(memberRole);
        userRepository.save(user);

        LoginResponse response = authService.login(new LoginRequest("test.login@aekd.cm", "Secret@123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.email()).isEqualTo("test.login@aekd.cm");
        assertThat(response.roles()).containsExactly("MEMBRE");
    }

    @Test
    void loginFailsWithWrongPassword() {
        Role memberRole = roleRepository.findByName(RoleName.MEMBRE).orElseThrow();
        User user = new User("test.badpass@aekd.cm", passwordEncoder.encode("Secret@123"));
        user.addRole(memberRole);
        userRepository.save(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test.badpass@aekd.cm", "wrong")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void loginFailsWithUnknownEmail() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@aekd.cm", "whatever")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void pendingAccountWithTheRightPasswordIsToldItAwaitsValidation() {
        // decisions.md n° 54 : réponse 403 distincte, l'interface affiche la page d'attente.
        User user = new User("test.pending@aekd.cm", passwordEncoder.encode("Secret@123"));
        user.addRole(roleRepository.findByName(RoleName.MEMBRE).orElseThrow());
        user.setStatus(UserStatus.PENDING_VALIDATION);
        userRepository.save(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test.pending@aekd.cm", "Secret@123")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(403))
                .hasMessageContaining("en attente de validation");
        // Mauvais mot de passe : rien ne révèle l'état du compte.
        assertThatThrownBy(() -> authService.login(new LoginRequest("test.pending@aekd.cm", "wrong")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void sessionIsRenewedWithTheRefreshTokenWhichRotatesAndIsRevokedOnLogout() {
        // decisions.md n° 54 : pas de reconnexion tant que la session est utilisée.
        User user = new User("test.refresh@aekd.cm", passwordEncoder.encode("Secret@123"));
        user.addRole(roleRepository.findByName(RoleName.MEMBRE).orElseThrow());
        userRepository.save(user);

        LoginResponse login = authService.login(new LoginRequest("test.refresh@aekd.cm", "Secret@123"));
        assertThat(login.refreshToken()).isNotBlank();

        LoginResponse renewed = authService.refresh(login.refreshToken());
        assertThat(renewed.token()).isNotBlank();
        assertThat(renewed.refreshToken()).isNotEqualTo(login.refreshToken());
        // Un jeton déjà utilisé ne sert plus.
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOf(ResponseStatusException.class);

        authService.logout(renewed.refreshToken());
        assertThatThrownBy(() -> authService.refresh(renewed.refreshToken()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aDisabledAccountCannotRenewItsSession() {
        User user = new User("test.disabled@aekd.cm", passwordEncoder.encode("Secret@123"));
        user.addRole(roleRepository.findByName(RoleName.MEMBRE).orElseThrow());
        userRepository.save(user);
        LoginResponse login = authService.login(new LoginRequest("test.disabled@aekd.cm", "Secret@123"));

        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);

        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .hasMessageContaining("plus actif");
    }
}
