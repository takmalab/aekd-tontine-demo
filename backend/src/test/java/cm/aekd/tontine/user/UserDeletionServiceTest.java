package cm.aekd.tontine.user;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.savings.SavingsDeposit;
import cm.aekd.tontine.savings.SavingsDepositRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Suppression d'un compte et de tout son historique (docs/decisions.md n° 41). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserDeletionServiceTest {

    @Autowired private UserDeletionService deletionService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private ContributionDefinitionRepository definitionRepository;
    @Autowired private ContributionPeriodRepository periodRepository;
    @Autowired private ContributionTransactionRepository transactionRepository;
    @Autowired private SavingsDepositRepository depositRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private User admin;
    private User treasurer;
    private Member leaving;
    private Member staying;
    private ContributionTransaction leavingPayment;
    private ContributionTransaction stayingPayment;

    @BeforeEach
    void setUp() {
        admin = createUser("del-admin", RoleName.ADMIN);
        treasurer = createUser("del-treso", RoleName.TRESORIER);
        leaving = memberRepository.save(new Member(createUser("del-leaving", RoleName.MEMBRE), "Membre Sortant",
                LocalDate.of(2024, 1, 1)));
        staying = memberRepository.save(new Member(createUser("del-staying", RoleName.MEMBRE), "Membre Restant",
                LocalDate.of(2024, 1, 1)));

        Session session = sessionRepository.save(new Session("Séance suppression", LocalDate.of(2026, 10, 3), "Douala", null));
        ContributionDefinition definition = definitionRepository.save(new ContributionDefinition("Cotisation test",
                null, new BigDecimal(1000), AmountMode.FIXED, ContributionFrequency.MONTHLY, true,
                Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        ContributionPeriod period = periodRepository.save(new ContributionPeriod(definition, session, null));

        leavingPayment = validatedPayment(leaving, period, "DEL-1");
        stayingPayment = validatedPayment(staying, period, "DEL-2");
        depositRepository.saveAndFlush(new SavingsDeposit(leaving, new BigDecimal(5000), LocalDate.now(),
                PaymentOperator.CASH, "DEL-EP", null, leaving.getUser()));
    }

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

    private ContributionTransaction validatedPayment(Member member, ContributionPeriod period, String ref) {
        ContributionTransaction tx = new ContributionTransaction(member, period, new BigDecimal(1000),
                PaymentOperator.CASH, ref, LocalDate.of(2026, 10, 3), null);
        tx.setStatus(ContributionTransactionStatus.VALIDATED);
        tx.setValidatedBy(treasurer);
        tx.setValidatedAt(Instant.now());
        return transactionRepository.saveAndFlush(tx);
    }

    private void loginAs(User user, RoleName role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private long count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }

    @Test
    void impactListsWhatWillBeErased() {
        loginAs(admin, RoleName.ADMIN);

        UserDeletionService.DeletionImpact impact = deletionService.impact(leaving.getUser().getId());

        assertThat(impact.memberFullName()).isEqualTo("Membre Sortant");
        assertThat(impact.contributionPayments()).isEqualTo(1);
        assertThat(impact.savingsDeposits()).isEqualTo(1);
        assertThat(deletionService.impact(treasurer.getId()).actionsAsAuthor()).isEqualTo(2);
    }

    @Test
    void deletingAMemberErasesTheAccountProfileAndHistory() {
        loginAs(admin, RoleName.ADMIN);
        UUID userId = leaving.getUser().getId();

        deletionService.delete(userId);
        // L'entrée d'audit (JPA) est écrite en base au commit ; on force l'écriture pour la lire en SQL.
        entityManager.flush();

        assertThat(count("SELECT count(*) FROM users WHERE id = ?", userId)).isZero();
        assertThat(count("SELECT count(*) FROM member WHERE id = ?", leaving.getId())).isZero();
        assertThat(count("SELECT count(*) FROM contribution_transaction WHERE id = ?", leavingPayment.getId())).isZero();
        assertThat(count("SELECT count(*) FROM savings_deposit WHERE member_id = ?", leaving.getId())).isZero();
        assertThat(count("SELECT count(*) FROM contribution_transaction WHERE id = ?", stayingPayment.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action = 'USER_DELETED' AND entity_id = ?", userId))
                .isEqualTo(1);
    }

    @Test
    void deletingATreasurerKeepsOtherMembersDataWithoutAuthor() {
        loginAs(admin, RoleName.ADMIN);

        deletionService.delete(treasurer.getId());

        assertThat(count("SELECT count(*) FROM contribution_transaction WHERE id = ?", stayingPayment.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT validated_by FROM contribution_transaction WHERE id = ?", UUID.class,
                stayingPayment.getId())).isNull();
    }

    @Test
    void cannotDeleteOwnAccount() {
        loginAs(admin, RoleName.ADMIN);

        assertThatThrownBy(() -> deletionService.delete(admin.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("propre compte");
    }
}
