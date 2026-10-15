package cm.aekd.tontine.loan;

import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Décision n° 49 : le trésorier et l'administrateur gèrent les politiques de prêt
 * (CLAUDE.md §20). Les appels passent par le contrôleur pour que
 * {@code @PreAuthorize} soit réellement évalué.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanPolicyPermissionTest {

    @Autowired private LoanController controller;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String prefix, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + roleName))));
    }

    private static LoanPolicyRequest policy(String name) {
        return new LoanPolicyRequest(name, null, new BigDecimal(10000), new BigDecimal(500000), 12,
                new BigDecimal("5.00"), null, null, 1);
    }

    @Test
    void treasurerManagesLoanPolicies() {
        loginAs("lp-treso", RoleName.TRESORIER);

        LoanPolicyResponse created = controller.createPolicy(policy("Politique du trésorier")).getBody();
        assertThat(created).isNotNull();

        LoanPolicyResponse updated = controller.updatePolicy(created.id(), policy("Politique modifiée"));
        assertThat(updated.name()).isEqualTo("Politique modifiée");
        assertThat(controller.deactivatePolicy(created.id()).active()).isFalse();
        assertThat(controller.activatePolicy(created.id()).active()).isTrue();
        assertThat(controller.deletePolicy(created.id()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void adminStillManagesLoanPolicies() {
        loginAs("lp-admin", RoleName.ADMIN);

        assertThat(controller.createPolicy(policy("Politique de l'admin")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void memberCannotChangeLoanPolicies() {
        loginAs("lp-membre", RoleName.MEMBRE);

        assertThatThrownBy(() -> controller.createPolicy(policy("Interdite")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(controller.listPolicies()).isNotNull();
    }
}
