package cm.aekd.tontine.dashboard;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinitionRequest;
import cm.aekd.tontine.contribution.ContributionDefinitionResponse;
import cm.aekd.tontine.contribution.ContributionDefinitionService;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionPeriodRequest;
import cm.aekd.tontine.contribution.ContributionPeriodResponse;
import cm.aekd.tontine.contribution.ContributionTransactionRequest;
import cm.aekd.tontine.contribution.ContributionTransactionService;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.MemberRefRequest;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.contribution.PaymentService;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    @Autowired
    private ContributionDefinitionService definitionService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ContributionTransactionService transactionService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Member createMember(RoleName roleName, String emailPrefix) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(emailPrefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        Member member = new Member(user, "Test " + emailPrefix, LocalDate.now());
        return memberRepository.save(member);
    }

    private void loginAsMember(Member member, RoleName roleName) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getUser().getEmail(), null, authorities));
    }

    @Test
    void noCurrentSessionMeansEmptyDashboards() {
        Member admin = createMember(RoleName.ADMIN, "dashadmin1");
        loginAsMember(admin, RoleName.ADMIN);

        AdminDashboardResponse dashboard = dashboardService.adminDashboard();
        assertThat(dashboard.currentSession()).isNull();
        assertThat(dashboard.mandatoryContributionsExpected()).isZero();
        assertThat(dashboard.amountCollected()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void memberDashboardSeparatesPaidPendingLateAndOptional() {
        Member treasurer = createMember(RoleName.TRESORIER, "dashtreso2");
        Member member = createMember(RoleName.MEMBRE, "dashmember2");

        Session today = sessionRepository.save(
                new Session("Séance en cours", LocalDate.now().minusDays(5), LocalDate.now().plusDays(5)));

        loginAsMember(treasurer, RoleName.TRESORIER);

        // Cotisation obligatoire payée
        ContributionDefinitionResponse paidDef = definitionService.create(new ContributionDefinitionRequest(
                "Payée", null, new BigDecimal(10000), AmountMode.FIXED, ContributionFrequency.MONTHLY,
                true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(paidDef.id(), new MemberRefRequest(member.getId()));
        definitionService.activate(paidDef.id());
        ContributionPeriodResponse paidPeriod = definitionService.addPeriod(paidDef.id(),
                new ContributionPeriodRequest(today.getId(), LocalDate.now().plusDays(10)));

        // Cotisation obligatoire en retard (date limite dépassée, rien payé)
        ContributionDefinitionResponse lateDef = definitionService.create(new ContributionDefinitionRequest(
                "En retard", null, new BigDecimal(5000), AmountMode.FIXED, ContributionFrequency.MONTHLY,
                true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(lateDef.id(), new MemberRefRequest(member.getId()));
        definitionService.activate(lateDef.id());
        definitionService.addPeriod(lateDef.id(), new ContributionPeriodRequest(today.getId(), LocalDate.now().minusDays(2)));

        // Cotisation facultative
        ContributionDefinitionResponse optionalDef = definitionService.create(new ContributionDefinitionRequest(
                "Facultative", null, new BigDecimal(2000), AmountMode.FIXED, ContributionFrequency.MONTHLY,
                false, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(optionalDef.id(), new MemberRefRequest(member.getId()));
        definitionService.activate(optionalDef.id());
        definitionService.addPeriod(optionalDef.id(), new ContributionPeriodRequest(today.getId(), null));

        loginAsMember(member, RoleName.MEMBRE);
        var declared = paymentService.declare(paidDef.id(), new ContributionTransactionRequest(
                paidPeriod.id(), new BigDecimal(10000), PaymentOperator.CASH, "REF-D1", LocalDate.now(), null));

        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(declared.id());

        loginAsMember(member, RoleName.MEMBRE);
        MemberDashboardResponse dashboard = dashboardService.memberDashboard();

        assertThat(dashboard.currentSession()).isNotNull();
        assertThat(dashboard.mandatoryContributionsCount()).isEqualTo(2);
        assertThat(dashboard.paidContributionsCount()).isEqualTo(1);
        assertThat(dashboard.lateContributionsCount()).isEqualTo(1);
        assertThat(dashboard.pendingContributionsCount()).isZero();
        assertThat(dashboard.optionalContributionsCount()).isEqualTo(1);
        assertThat(dashboard.individualSavings()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void adminDashboardAggregatesAcrossMembers() {
        Member treasurer = createMember(RoleName.TRESORIER, "dashtreso3");
        Member memberA = createMember(RoleName.MEMBRE, "dasha3");
        Member memberB = createMember(RoleName.MEMBRE, "dashb3");

        Session today = sessionRepository.save(
                new Session("Séance active", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)));

        loginAsMember(treasurer, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation Admin", null, new BigDecimal(15000), AmountMode.FIXED, ContributionFrequency.MONTHLY,
                true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(def.id(), new MemberRefRequest(memberA.getId()));
        definitionService.addParticipant(def.id(), new MemberRefRequest(memberB.getId()));
        definitionService.activate(def.id());
        ContributionPeriodResponse period = definitionService.addPeriod(def.id(),
                new ContributionPeriodRequest(today.getId(), LocalDate.now().plusDays(5)));

        loginAsMember(memberA, RoleName.MEMBRE);
        var declared = paymentService.declare(def.id(), new ContributionTransactionRequest(
                period.id(), new BigDecimal(15000), PaymentOperator.CASH, "REF-A1", LocalDate.now(), null));

        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(declared.id());

        AdminDashboardResponse dashboard = dashboardService.adminDashboard();
        assertThat(dashboard.memberCount()).isGreaterThanOrEqualTo(3);
        assertThat(dashboard.mandatoryContributionsExpected()).isEqualTo(2);
        assertThat(dashboard.paymentsValidated()).isEqualTo(1);
        assertThat(dashboard.amountCollected()).isEqualByComparingTo(new BigDecimal(15000));
        assertThat(dashboard.tontineFundBalance()).isGreaterThanOrEqualTo(new BigDecimal(15000));
    }
}
