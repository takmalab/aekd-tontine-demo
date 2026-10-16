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
    private SanctionPaymentService sanctionPaymentService;

    @Autowired
    private cm.aekd.tontine.fund.FundService fundService;

    @Autowired
    private cm.aekd.tontine.fund.AccountOperationService operationService;

    @Autowired
    private cm.aekd.tontine.loan.GuarantorStandingService guarantorStandingService;

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
    void unusedRuleCanBeEditedDeactivatedAndDeleted() {
        Member treasurer = createMember(RoleName.TRESORIER, "rulemgmt1");
        Member lateMember = createMember(RoleName.MEMBRE, "rulemgmtlate1");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, lateMember,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));
        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));
        assertThat(rule.used()).isFalse();

        // Changement de type possible tant que la règle n'a pas servi.
        SanctionRuleResponse edited = ruleService.update(rule.id(), new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.IN_KIND, 5, null, "Apporter les boissons"));
        assertThat(edited.type()).isEqualTo(SanctionType.IN_KIND);
        assertThat(edited.lateDaysThreshold()).isEqualTo(5);
        assertThat(edited.monetaryAmount()).isNull();

        // Désactivée : plus de candidats, application refusée ; réactivable.
        assertThat(ruleService.setActive(rule.id(), false).active()).isFalse();
        assertThat(appliedService.findCandidates(rule.id())).isEmpty();
        assertThatThrownBy(() -> appliedService.apply(rule.id(),
                new ApplySanctionRequest(lateMember.getId(), period.id())))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(ruleService.setActive(rule.id(), true).active()).isTrue();

        ruleService.delete(rule.id());
        assertThat(ruleService.findByDefinition(period.contributionDefinitionId())).isEmpty();
    }

    @Test
    void usedRuleKeepsItsTypeAndCannotBeDeleted() {
        Member treasurer = createMember(RoleName.TRESORIER, "rulemgmt2");
        Member lateMember = createMember(RoleName.MEMBRE, "rulemgmtlate2");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, lateMember,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));
        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));
        AppliedSanctionResponse applied = appliedService.apply(rule.id(),
                new ApplySanctionRequest(lateMember.getId(), period.id()));

        assertThatThrownBy(() -> ruleService.update(rule.id(), new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.IN_KIND, 3, null, "Nettoyage")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> ruleService.delete(rule.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("désactivez");

        // Le montant change pour l'avenir ; la sanction déjà appliquée garde le sien.
        SanctionRuleResponse edited = ruleService.update(rule.id(), new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(3000), null));
        assertThat(edited.used()).isTrue();
        assertThat(edited.monetaryAmount()).isEqualByComparingTo("3000");
        assertThat(appliedService.findByDefinition(period.contributionDefinitionId()))
                .filteredOn(s -> s.id().equals(applied.id()))
                .extracting(AppliedSanctionResponse::amount)
                .containsExactly(new BigDecimal(2000));
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

    @Test
    void treasurerCannotApplyOrCancelOwnSanction() {
        Member admin = createMember(RoleName.ADMIN, "sancadmin7");
        Member treasurer = createMember(RoleName.TRESORIER, "sanctreso7");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, treasurer,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));

        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));
        assertThatThrownBy(() -> appliedService.apply(rule.id(),
                new ApplySanctionRequest(treasurer.getId(), period.id())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vous concerne");

        loginAsMember(admin, RoleName.ADMIN);
        AppliedSanctionResponse applied = appliedService.apply(rule.id(),
                new ApplySanctionRequest(treasurer.getId(), period.id()));

        loginAsMember(treasurer, RoleName.TRESORIER);
        assertThatThrownBy(() -> appliedService.cancel(applied.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vous concerne");
    }

    private SanctionPaymentDtos.DeclareRequest payment(long amount, String reference) {
        return new SanctionPaymentDtos.DeclareRequest(BigDecimal.valueOf(amount), LocalDate.now(),
                PaymentOperator.MTN_MOMO, reference, null);
    }

    @Test
    void monetarySanctionIsPaidInInstalmentsAndFeedsTheSanctionsAccount() {
        // decisions.md n° 50 : le membre déclare, le trésorier valide ; seul le validé entre au compte.
        Member treasurer = createMember(RoleName.TRESORIER, "sanpay-treso");
        Member lateMember = createMember(RoleName.MEMBRE, "sanpay-late");
        Member other = createMember(RoleName.MEMBRE, "sanpay-other");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, lateMember,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));
        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));
        AppliedSanctionResponse sanction = appliedService.apply(rule.id(),
                new ApplySanctionRequest(lateMember.getId(), period.id()));
        BigDecimal accountBefore = fundService.sanctionsBalance();

        loginAsMember(other, RoleName.MEMBRE);
        assertThatThrownBy(() -> sanctionPaymentService.declare(sanction.id(), payment(500, "AUTRE")))
                .hasMessageContaining("vos propres sanctions");

        loginAsMember(lateMember, RoleName.MEMBRE);
        SanctionPaymentDtos.PaymentResponse first = sanctionPaymentService.declare(sanction.id(), payment(1500, "SAN-1"));
        assertThat(first.status()).isEqualTo(SanctionPaymentStatus.PENDING);
        // 1 500 déjà en attente : il ne reste que 500 à déclarer.
        assertThatThrownBy(() -> sanctionPaymentService.declare(sanction.id(), payment(1000, "SAN-2")))
                .hasMessageContaining("reste à payer");
        assertThat(fundService.sanctionsBalance()).isEqualByComparingTo(accountBefore);

        loginAsMember(treasurer, RoleName.TRESORIER);
        sanctionPaymentService.validate(first.id());
        assertThat(fundService.sanctionsBalance()).isEqualByComparingTo(accountBefore.add(new BigDecimal(1500)));

        loginAsMember(lateMember, RoleName.MEMBRE);
        SanctionPaymentDtos.PaymentResponse second = sanctionPaymentService.declare(sanction.id(), payment(500, "SAN-3"));
        loginAsMember(treasurer, RoleName.TRESORIER);
        sanctionPaymentService.validate(second.id());

        loginAsMember(lateMember, RoleName.MEMBRE);
        AppliedSanctionResponse paid = appliedService.findMine().stream()
                .filter(a -> a.id().equals(sanction.id())).findFirst().orElseThrow();
        assertThat(paid.paidAmount()).isEqualByComparingTo(new BigDecimal(2000));
        assertThat(paid.pendingAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // De l'argent est rattaché à la sanction : elle ne peut plus être annulée.
        loginAsMember(treasurer, RoleName.TRESORIER);
        assertThatThrownBy(() -> appliedService.cancel(sanction.id()))
                .hasMessageContaining("paiement");
    }

    @Test
    void expensesAndTransfersMoveOnlyOperableAccountsAndNeverBelowZero() {
        // decisions.md n° 50 : dépenses et virements seulement sur Sanctions, Fond de caisse et Ration.
        Member treasurer = createMember(RoleName.TRESORIER, "accop-treso");
        Member lateMember = createMember(RoleName.MEMBRE, "accop-late");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, lateMember,
                LocalDate.of(2026, 9, 5), new BigDecimal(10000));
        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));
        AppliedSanctionResponse sanction = appliedService.apply(rule.id(),
                new ApplySanctionRequest(lateMember.getId(), period.id()));
        loginAsMember(lateMember, RoleName.MEMBRE);
        SanctionPaymentDtos.PaymentResponse paid = sanctionPaymentService.declare(sanction.id(), payment(2000, "ACC-1"));
        loginAsMember(treasurer, RoleName.TRESORIER);
        sanctionPaymentService.validate(paid.id());

        BigDecimal sanctions = fundService.sanctionsBalance();
        BigDecimal cashFund = fundService.cashFundBalance();
        BigDecimal tontine = fundService.tontineFundBalance();

        assertThatThrownBy(() -> operationService.record(operation(cm.aekd.tontine.fund.AccountOperationType.EXPENSE,
                cm.aekd.tontine.fund.FundAccount.TONTINE_FUND, null, 100)))
                .hasMessageContaining("protégé");
        assertThatThrownBy(() -> operationService.record(operation(cm.aekd.tontine.fund.AccountOperationType.TRANSFER,
                cm.aekd.tontine.fund.FundAccount.SANCTIONS, cm.aekd.tontine.fund.FundAccount.SAVINGS, 100)))
                .hasMessageContaining("protégé");
        assertThatThrownBy(() -> operationService.record(operation(cm.aekd.tontine.fund.AccountOperationType.EXPENSE,
                cm.aekd.tontine.fund.FundAccount.SANCTIONS, null, sanctions.intValue() + 1)))
                .hasMessageContaining("Solde insuffisant");

        operationService.record(operation(cm.aekd.tontine.fund.AccountOperationType.TRANSFER,
                cm.aekd.tontine.fund.FundAccount.SANCTIONS, cm.aekd.tontine.fund.FundAccount.CASH_FUND, 500));
        operationService.record(operation(cm.aekd.tontine.fund.AccountOperationType.EXPENSE,
                cm.aekd.tontine.fund.FundAccount.SANCTIONS, null, 1000));

        assertThat(fundService.sanctionsBalance()).isEqualByComparingTo(sanctions.subtract(new BigDecimal(1500)));
        assertThat(fundService.cashFundBalance()).isEqualByComparingTo(cashFund.add(new BigDecimal(500)));
        assertThat(fundService.tontineFundBalance()).isEqualByComparingTo(tontine);
    }

    private static cm.aekd.tontine.fund.AccountOperationDtos.OperationRequest operation(
            cm.aekd.tontine.fund.AccountOperationType type, cm.aekd.tontine.fund.FundAccount source,
            cm.aekd.tontine.fund.FundAccount target, int amount) {
        return new cm.aekd.tontine.fund.AccountOperationDtos.OperationRequest(type, source, target,
                new BigDecimal(amount), LocalDate.of(2026, 10, 1), "Test", null);
    }

    @Test
    void memberWithLateContributionAndUnpaidSanctionIsNotUpToDateAsGuarantor() {
        // decisions.md n° 52 : « à jour » = cotisations, sanctions et prêts.
        Member treasurer = createMember(RoleName.TRESORIER, "standing-treso");
        Member lateMember = createMember(RoleName.MEMBRE, "standing-late");
        ContributionPeriodResponse period = setupMandatoryDefinition(treasurer, lateMember,
                LocalDate.now().minusDays(10), new BigDecimal(10000));
        assertThat(guarantorStandingService.issuesOf(lateMember.getId()))
                .containsExactly("1 cotisation(s) obligatoire(s) en retard");

        loginAsMember(treasurer, RoleName.TRESORIER);
        SanctionRuleResponse rule = ruleService.create(new SanctionRuleRequest(
                period.contributionDefinitionId(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));
        appliedService.apply(rule.id(), new ApplySanctionRequest(lateMember.getId(), period.id()));

        assertThat(guarantorStandingService.issuesOf(lateMember.getId()))
                .hasSize(2)
                .anyMatch(issue -> issue.startsWith("1 sanction(s) non payée(s)"));
        assertThat(guarantorStandingService.issuesOf(treasurer.getId())).isEmpty();
    }
}
