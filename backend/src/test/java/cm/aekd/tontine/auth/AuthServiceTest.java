package cm.aekd.tontine.auth;

import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
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
}
