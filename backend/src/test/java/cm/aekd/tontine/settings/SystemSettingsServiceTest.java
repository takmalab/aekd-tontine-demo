package cm.aekd.tontine.settings;

import cm.aekd.tontine.auth.AuthService;
import cm.aekd.tontine.auth.LoginRequest;
import cm.aekd.tontine.document.AekdPdf;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.settings.SystemSettingsService.SettingsRequest;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Paramètres système (docs/decisions.md n° 57). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SystemSettingsServiceTest {

    @Autowired
    private SystemSettingsService service;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** La transaction est annulée : la copie en mémoire doit l'être aussi. */
    @AfterEach
    void forgetCachedSettings() {
        org.springframework.test.context.transaction.TestTransaction.flagForRollback();
        org.springframework.test.context.transaction.TestTransaction.end();
        service.reload();
    }

    private User user(String email, RoleName... roles) {
        User user = new User(email, passwordEncoder.encode("Secret@123"));
        for (RoleName role : roles) {
            user.addRole(roleRepository.findByName(role).orElseThrow());
        }
        return userRepository.save(user);
    }

    private static SettingsRequest request(Integer sessionDays, Integer otpTtl, boolean maintenance) {
        return new SettingsRequest("Association Test", "AT", "Douala", null, null,
                sessionDays, otpTtl, null, null, null, maintenance, "Retour à 18 h");
    }

    @Test
    void emptyRulesUseTheServerConfigurationAndSetOnesWin() {
        assertThat(service.sessionDays()).isEqualTo(30);
        assertThat(service.otpTtl()).isEqualTo(Duration.ofMinutes(5));

        service.update(request(7, 10, false));

        assertThat(service.sessionDays()).isEqualTo(7);
        assertThat(service.otpTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(service.otpMaxAttempts()).isEqualTo(5);
        assertThat(AekdPdf.associationName()).isEqualTo("Association Test");
    }

    @Test
    void maintenanceLetsOnlyTheSuperAdministratorIn() {
        user("maint.member@test.aekd.cm", RoleName.MEMBRE);
        user("maint.admin@test.aekd.cm", RoleName.ADMIN);
        user("maint.root@test.aekd.cm", RoleName.SUPER_ADMIN, RoleName.ADMIN);

        service.update(request(null, null, true));

        for (String email : new String[]{"maint.member@test.aekd.cm", "maint.admin@test.aekd.cm"}) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(email, "Secret@123")))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode().value()).isEqualTo(503))
                    .hasMessageContaining("Retour à 18 h");
        }
        assertThat(authService.login(new LoginRequest("maint.root@test.aekd.cm", "Secret@123")).token()).isNotBlank();
    }
}
