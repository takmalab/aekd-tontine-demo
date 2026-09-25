package cm.aekd.tontine.sanction;

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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SanctionFlowTest {

    @Autowired
    private ContributionDefinitionService definitionService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ContributionTransactionService transactionService;

    @Autowired
    private SanctionRuleService ruleService;

    @Autowired
    private AppliedSanctionService appliedService;

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

    private ContributionPeriodResponse setupMandatoryDefinition(Member treasurer, Member participant,
                                                                 LocalDate dueDate, BigDecimal amount) {
        loginAsMember(treasurer, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation Sanctionnable", null, amount, AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(def.id(), new MemberRefRequest(participant.getId()));
        definitionService.activate(def.id());
        Session session = sessionRepository.save(
                new Session("Session Sanction", LocalDate.of(2026, 9, 1), null, null));
        return definitionService.addPeriod(def.id(), new ContributionPeriodRequest(session.getId(), dueDate));
    }

    @Test
    void ruleCannotBeCreatedForOptionalContribution() {
        Member treasurer = createMember(RoleName.TRESORIER, "sanctreso1");
        loginAsMember(treasurer, RoleName.TRESORIER);
        ContributionDefinitionResponse optionalDef = definitionService.create(new ContributionDefinitionRequest(
                "Voir bébé", null, new BigDecimal(1000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, false, Visibility.PUBLIC, FundDestination.TONTINE_FUND));

        assertThatThrownBy(() -> ruleService.create(new SanctionRuleRequest(
                optionalDef.id(), SanctionType.MONETARY, 3, new BigDecimal(2000), null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void memberPastDueWithoutValidatedPaymentIsEligibleAndCanBeSanctioned() {
        Member treasurer = createMember(RoleName.TRESORIER, "sanctreso2");
        Member lateMember = createMember(RoleName.MEMBRE, "sanclate2");

        // Date limite largement dépassée (avant aujourd'hui, 2026-09-24 dans cette session)
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, lateMember,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));

        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));

        List<SanctionCandidateResponse> candidates = appliedService.findCandidates(rule.id());
        assertThat(candidates).extracting(SanctionCandidateResponse::memberId).contains(lateMember.getId());

        AppliedSanctionResponse applied = appliedService.apply(rule.id(),
                new ApplySanctionRequest(lateMember.getId(), period.id()));
        assertThat(applied.amount()).isEqualByComparingTo(new BigDecimal(2000));
        assertThat(applied.status()).isEqualTo(AppliedSanctionStatus.APPLIED);

        // Ne doit plus apparaître comme candidat une fois sanctionné
        assertThat(appliedService.findCandidates(rule.id()))
                .extracting(SanctionCandidateResponse::memberId).doesNotContain(lateMember.getId());

        // Double application refusée
        assertThatThrownBy(() -> appliedService.apply(rule.id(),
                new ApplySanctionRequest(lateMember.getId(), period.id())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void memberWithValidatedPaymentIsNotEligible() {
        Member treasurer = createMember(RoleName.TRESORIER, "sanctreso3");
        Member payer = createMember(RoleName.MEMBRE, "sancpayer3");

        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, payer,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));

        loginAsMember(payer, RoleName.MEMBRE);
        var tx = paymentService.declare(period.contributionDefinitionId(), new ContributionTransactionRequest(
                period.id(), new BigDecimal(10000), PaymentOperator.CASH, "REF-S1", LocalDate.of(2026, 9, 4), null));
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(tx.id());

        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));

        assertThat(appliedService.findCandidates(rule.id()))
                .extracting(SanctionCandidateResponse::memberId).doesNotContain(payer.getId());

        assertThatThrownBy(() -> appliedService.apply(rule.id(),
                new ApplySanctionRequest(payer.getId(), period.id())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void memberNotYetPastThresholdIsNotEligible() {
        Member treasurer = createMember(RoleName.TRESORIER, "sanctreso4");
        Member member = createMember(RoleName.MEMBRE, "sancnotyet4");

        // Date limite dans le futur lointain : jamais en retard
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, member,
                LocalDate.of(2027, 1, 1), new BigDecimal(10000));

        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));

        assertThatThrownBy(() -> appliedService.apply(rule.id(),
                new ApplySanctionRequest(member.getId(), period.id())))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void inKindSanctionRequiresDescriptionNotAmount() {
        Member treasurer = createMember(RoleName.TRESORIER, "sanctreso5");
        Member member = createMember(RoleName.MEMBRE, "sancinkind5");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, member,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));

        loginAsMember(treasurer, RoleName.TRESORIER);
        assertThatThrownBy(() -> ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.IN_KIND, 3, null, null)))
                .isInstanceOf(ResponseStatusException.class);

        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.IN_KIND, 3, null, "Apporter 3kg de riz"));
        AppliedSanctionResponse applied = appliedService.apply(rule.id(),
                new ApplySanctionRequest(member.getId(), period.id()));
        assertThat(applied.amount()).isNull();
        assertThat(applied.description()).isEqualTo("Apporter 3kg de riz");
    }

    @Test
    void cancelReleasesTheSanctionSlot() {
        Member treasurer = createMember(RoleName.TRESORIER, "sanctreso6");
        Member member = createMember(RoleName.MEMBRE, "sanccancel6");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, member,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));

        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));
        AppliedSanctionResponse applied = appliedService.apply(rule.id(),
                new ApplySanctionRequest(member.getId(), period.id()));

        AppliedSanctionResponse cancelled = appliedService.cancel(applied.id());
        assertThat(cancelled.status()).isEqualTo(AppliedSanctionStatus.CANCELLED);

        // Une nouvelle application redevient possible après annulation
        AppliedSanctionResponse reapplied = appliedService.apply(rule.id(),
                new ApplySanctionRequest(member.getId(), period.id()));
        assertThat(reapplied.status()).isEqualTo(AppliedSanctionStatus.APPLIED);
    }
}
