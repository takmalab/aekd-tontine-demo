package cm.aekd.tontine.security;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.savings.SavingsController;
import cm.aekd.tontine.savings.SavingsDtos.DepositRequest;
import cm.aekd.tontine.savings.SavingsDtos.EntryResponse;
import cm.aekd.tontine.savings.SavingsService;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Décision n° 40 : l'ADMIN a tous les droits du TRESORIER. Les appels passent par
 * le contrôleur (proxy Spring) pour que {@code @PreAuthorize} soit réellement évalué.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleHierarchyTest {

    @Autowired private SavingsController savingsController;
    @Autowired private SavingsService savingsService;
    @Autowired private CurrentUserProvider currentUserProvider;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User createUser(String prefix, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        return userRepository.save(user);
    }

    private void loginAs(String email, RoleName role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private EntryResponse pendingDeposit(String prefix) {
        Member saver = memberRepository.save(new Member(createUser(prefix, RoleName.MEMBRE), "Test " + prefix,
                LocalDate.of(2024, 1, 1)));
        loginAs(saver.getUser().getEmail(), RoleName.MEMBRE);
        return savingsService.declare(new DepositRequest(new BigDecimal(1000), LocalDate.now(),
                PaymentOperator.CASH, "RH-" + prefix, null));
    }

    @Test
    void adminPassesTreasurerOnlyEndpoints() {
        EntryResponse deposit = pendingDeposit("rh-saver1");
        createUser("rh-admin", RoleName.ADMIN);
        loginAs("rh-admin@test.aekd.cm", RoleName.ADMIN);

        // @PreAuthorize("hasRole('TRESORIER')")
        assertThat(savingsController.validate(deposit.id()).status()).isEqualTo("VALIDATED");
        assertThat(currentUserProvider.hasAnyRole("TRESORIER")).isTrue();
        assertThat(currentUserProvider.hasAnyRole("MEMBRE")).isFalse();
    }

    @Test
    void memberIsStillRefusedOnTreasurerOnlyEndpoints() {
        EntryResponse deposit = pendingDeposit("rh-saver2");
        createUser("rh-other", RoleName.MEMBRE);
        loginAs("rh-other@test.aekd.cm", RoleName.MEMBRE);

        assertThatThrownBy(() -> savingsController.reject(deposit.id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(currentUserProvider.hasAnyRole("TRESORIER")).isFalse();
    }

    @Test
    void treasurerDoesNotGainAdminRights() {
        createUser("rh-treso", RoleName.TRESORIER);
        loginAs("rh-treso@test.aekd.cm", RoleName.TRESORIER);

        assertThat(currentUserProvider.hasAnyRole("ADMIN")).isFalse();
    }
}
