package cm.aekd.tontine.reset;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLog;
import cm.aekd.tontine.audit.AuditLogRepository;
import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionRepository;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Réinitialisation des données par le super administrateur (docs/decisions.md n° 63). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataResetServiceTest {

    @Autowired private DataResetService service;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private ContributionDefinitionRepository definitionRepository;
    @Autowired private ContributionPeriodRepository periodRepository;
    @Autowired private ContributionTransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuditLogRepository auditLogRepository;

    private User superAdmin;
    private User otherSuperAdmin;
    private User admin;
    private Member member;

    @BeforeEach
    void setUp() {
        superAdmin = createUser("reset-super", RoleName.SUPER_ADMIN);
        otherSuperAdmin = createUser("reset-super2", RoleName.SUPER_ADMIN);
        admin = createUser("reset-admin", RoleName.ADMIN);
        member = memberRepository.save(new Member(createUser("reset-member", RoleName.MEMBRE), "Membre Reset",
                LocalDate.of(2024, 1, 1)));

        Session session = sessionRepository.save(new Session("Séance reset", LocalDate.of(2026, 10, 3), "Douala", null));
        ContributionDefinition definition = definitionRepository.save(new ContributionDefinition("Cotisation 50 000 FCFA",
                null, new BigDecimal(50000), AmountMode.FIXED, ContributionFrequency.MONTHLY, true,
                Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        ContributionPeriod period = periodRepository.save(new ContributionPeriod(definition, session, null));
        transactionRepository.saveAndFlush(new ContributionTransaction(member, period, new BigDecimal(50000),
                PaymentOperator.CASH, "RESET-1", LocalDate.of(2026, 10, 3), null));
        jdbc.update("""
                INSERT INTO sanction_rule (id, contribution_definition_id, type, late_days_threshold, monetary_amount,
                                           active, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, 'MONETARY', 3, 2000, TRUE, now(), now())
                """, definition.getId());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                superAdmin.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User createUser(String prefix, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("secret"));
        user.addRole(role);
        return userRepository.save(user);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    void contributionsResetTakesItsDependenciesAndKeepsSessionsAndAccounts() {
        DataResetDtos.ResetResult result = service.reset(new DataResetDtos.ResetRequest(
                List.of(DataResetCategory.CONTRIBUTIONS), "réinitialiser", "secret"));

        assertThat(result.categories()).containsExactlyInAnyOrder(DataResetCategory.CONTRIBUTIONS,
                DataResetCategory.SANCTIONS, DataResetCategory.PAYOUTS, DataResetCategory.ACCOUNT_OPERATIONS);
        assertThat(count("contribution_definition")).isZero();
        assertThat(count("contribution_transaction")).isZero();
        assertThat(count("sanction_rule")).isZero();
        assertThat(count("session")).isPositive();
        assertThat(userRepository.findById(member.getUser().getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT data_reset_at IS NOT NULL FROM system_settings WHERE id = 1",
                Boolean.class)).isTrue();
        assertThat(auditLogRepository.findAllByOrderByOccurredAtDesc())
                .extracting(AuditLog::getAction).contains(AuditAction.DATA_RESET);
    }

    @Test
    void membersResetKeepsSuperAdministratorsOnly() {
        service.reset(new DataResetDtos.ResetRequest(List.of(DataResetCategory.MEMBERS), "REINITIALISER", "secret"));

        assertThat(userRepository.findById(superAdmin.getId())).isPresent();
        assertThat(userRepository.findById(otherSuperAdmin.getId())).isPresent();
        assertThat(userRepository.findById(admin.getId())).isEmpty();
        assertThat(userRepository.findById(member.getUser().getId())).isEmpty();
        assertThat(count("contribution_transaction")).isZero(); // historique du membre effacé avec lui
        assertThat(count("contribution_definition")).isPositive(); // la cotisation elle-même reste
    }

    @Test
    void wrongConfirmationOrPasswordIsRefusedAndNothingIsDeleted() {
        assertThatThrownBy(() -> service.reset(new DataResetDtos.ResetRequest(
                List.of(DataResetCategory.CONTRIBUTIONS), "OUI", "secret")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("REINITIALISER");
        assertThatThrownBy(() -> service.reset(new DataResetDtos.ResetRequest(
                List.of(DataResetCategory.CONTRIBUTIONS), "REINITIALISER", "mauvais")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Mot de passe");
        assertThat(count("contribution_definition")).isPositive();
    }

    @Test
    void previewListsEveryCategoryWithItsDependencies() {
        List<DataResetDtos.CategoryPreview> preview = service.preview();

        assertThat(preview).extracting(DataResetDtos.CategoryPreview::category)
                .containsExactly(DataResetCategory.values());
        DataResetDtos.CategoryPreview members = preview.stream()
                .filter(p -> p.category() == DataResetCategory.MEMBERS).findFirst().orElseThrow();
        // Ni les super administrateurs ni le compte courant ne sont comptés.
        assertThat(members.items().get(0).count()).isEqualTo(jdbc.queryForObject("""
                SELECT count(*) FROM users u WHERE NOT EXISTS (SELECT 1 FROM user_roles ur JOIN role r
                    ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.name = 'SUPER_ADMIN')
                """, Long.class));
        DataResetDtos.CategoryPreview policies = preview.stream()
                .filter(p -> p.category() == DataResetCategory.LOAN_POLICIES).findFirst().orElseThrow();
        assertThat(policies.implies()).containsExactly(DataResetCategory.LOANS);
    }
}
