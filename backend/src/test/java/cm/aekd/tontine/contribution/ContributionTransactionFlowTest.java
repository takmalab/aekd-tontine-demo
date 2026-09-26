package cm.aekd.tontine.contribution;

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
class ContributionTransactionFlowTest {

    @Autowired
    private ContributionDefinitionService definitionService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ContributionTransactionService transactionService;

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

    @Autowired
    private ContributionPeriodStatusService periodStatusService;

    @Autowired
    private ContributionPeriodRepository periodRepository;

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

    private ContributionPeriodResponse setupActiveDefinitionWithParticipant(Member participant) {
        loginAsMember(participant, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation 50 000", null, new BigDecimal(50000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(def.id(), new MemberRefRequest(participant.getId()));
        definitionService.activate(def.id());
        Session session = sessionRepository.save(
                new Session("Octobre 2026", LocalDate.of(2026, 10, 1), null, null));
        return definitionService.addPeriod(def.id(), new ContributionPeriodRequest(session.getId(), null));
    }

    @Test
    void memberDeclaresPaymentAndTreasurerValidatesIt() {
        Member member = createMember(RoleName.MEMBRE, "payer1");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(member);
        Member treasurer = createMember(RoleName.TRESORIER, "treso1");

        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse declared = paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(50000), PaymentOperator.MTN_MOMO,
                        "REF-001", LocalDate.of(2026, 10, 5), null));
        assertThat(declared.status()).isEqualTo(ContributionTransactionStatus.PENDING);

        loginAsMember(treasurer, RoleName.TRESORIER);
        assertThat(transactionService.findPending()).extracting(ContributionTransactionResponse::id)
                .contains(declared.id());

        ContributionTransactionResponse validated = transactionService.validate(declared.id());
        assertThat(validated.status()).isEqualTo(ContributionTransactionStatus.VALIDATED);
        assertThat(validated.validatedByEmail()).isEqualTo(treasurer.getUser().getEmail());
    }

    @Test
    void nonParticipantCannotDeclarePayment() {
        Member participant = createMember(RoleName.MEMBRE, "payer2");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(participant);
        Member outsider = createMember(RoleName.MEMBRE, "outsider1");

        loginAsMember(outsider, RoleName.MEMBRE);
        assertThatThrownBy(() -> paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(50000), PaymentOperator.CASH,
                        "REF-002", LocalDate.of(2026, 10, 5), null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void fixedAmountAboveRemainingIsRejected() {
        Member member = createMember(RoleName.MEMBRE, "payer3");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(member);

        loginAsMember(member, RoleName.MEMBRE);
        assertThatThrownBy(() -> declare(period, 60000, "REF-003"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("reste dû")
                .hasMessageContaining("50000");
        assertThatThrownBy(() -> declare(period, 0, "REF-003B"))
                .isInstanceOf(ResponseStatusException.class);
    }

    private ContributionTransactionResponse declare(ContributionPeriodResponse period, long amount, String ref) {
        return paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(amount), PaymentOperator.CASH,
                        ref, LocalDate.of(2026, 10, 5), null));
    }

    private ContributionPeriodStatus statusOf(ContributionPeriodResponse period, Member member) {
        return periodStatusService.resolve(periodRepository.findById(period.id()).orElseThrow(), member.getId());
    }

    @Test
    void partialPaymentsAccumulateUntilFullyPaid() {
        Member member = createMember(RoleName.MEMBRE, "partial1");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(member);
        Member treasurer = createMember(RoleName.TRESORIER, "tresopartial1");

        // 1er versement partiel : en attente, puis validé -> PARTIAL
        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse first = declare(period, 20000, "PART-1");
        assertThat(first.amount()).isEqualByComparingTo("20000");
        assertThat(statusOf(period, member)).isEqualTo(ContributionPeriodStatus.PENDING);

        // Une seule déclaration en attente à la fois
        assertThatThrownBy(() -> declare(period, 10000, "PART-1B"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("en attente");

        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(first.id());
        assertThat(statusOf(period, member)).isEqualTo(ContributionPeriodStatus.PARTIAL);
        ContributionPeriod entity = periodRepository.findById(period.id()).orElseThrow();
        assertThat(periodStatusService.remainingAmount(entity, member.getId())).isEqualByComparingTo("30000");
        assertThat(periodStatusService.isFullyPaid(entity, member.getId())).isFalse();

        // Plus que le reste dû : refusé ; le solde exact : accepté
        loginAsMember(member, RoleName.MEMBRE);
        assertThatThrownBy(() -> declare(period, 40000, "PART-2X"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("30000");
        ContributionTransactionResponse second = declare(period, 30000, "PART-2");
        assertThat(statusOf(period, member)).isEqualTo(ContributionPeriodStatus.PENDING);

        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(second.id());
        assertThat(statusOf(period, member)).isEqualTo(ContributionPeriodStatus.PAID);
        assertThat(periodStatusService.isFullyPaid(entity, member.getId())).isTrue();

        // Entièrement payée : plus aucune déclaration possible
        loginAsMember(member, RoleName.MEMBRE);
        assertThatThrownBy(() -> declare(period, 1000, "PART-3"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("entièrement payée");
    }

    @Test
    void omittedAmountDefaultsToRemainingForFixedContribution() {
        Member member = createMember(RoleName.MEMBRE, "partial2");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(member);
        Member treasurer = createMember(RoleName.TRESORIER, "tresopartial2");

        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse first = declare(period, 15000, "DEF-1");
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(first.id());

        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse rest = paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), null, PaymentOperator.CASH,
                        "DEF-2", LocalDate.of(2026, 10, 6), null));
        assertThat(rest.amount()).isEqualByComparingTo("35000");
    }

    @Test
    void partialPaymentPastDueDateIsPartialNotLate() {
        Member member = createMember(RoleName.MEMBRE, "partial3");
        Member treasurer = createMember(RoleName.TRESORIER, "tresopartial3");
        loginAsMember(treasurer, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation échue", null, new BigDecimal(10000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(def.id(), new MemberRefRequest(member.getId()));
        definitionService.activate(def.id());
        Session session = sessionRepository.save(new Session("Séance passée", LocalDate.now().minusDays(20), null, null));
        ContributionPeriodResponse period = definitionService.addPeriod(def.id(),
                new ContributionPeriodRequest(session.getId(), LocalDate.now().minusDays(5)));

        assertThat(statusOf(period, member)).isEqualTo(ContributionPeriodStatus.LATE);

        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse tx = declare(period, 4000, "LATE-1");
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(tx.id());
        assertThat(statusOf(period, member)).isEqualTo(ContributionPeriodStatus.PARTIAL);
    }

    @Test
    void voluntaryContributionKeepsSingleActiveDeclarationRule() {
        Member member = createMember(RoleName.MEMBRE, "volunteer1");
        Member treasurer = createMember(RoleName.TRESORIER, "tresovol1");
        loginAsMember(treasurer, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Épargne", null, null, AmountMode.VOLUNTARY,
                ContributionFrequency.MONTHLY, false, Visibility.PUBLIC, FundDestination.INDIVIDUAL_SAVINGS));
        definitionService.addParticipant(def.id(), new MemberRefRequest(member.getId()));
        definitionService.activate(def.id());
        Session session = sessionRepository.save(new Session("Séance épargne", LocalDate.of(2026, 10, 3), null, null));
        ContributionPeriodResponse period = definitionService.addPeriod(def.id(),
                new ContributionPeriodRequest(session.getId(), null));

        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse tx = declare(period, 7500, "VOL-1");
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(tx.id());
        assertThat(statusOf(period, member)).isEqualTo(ContributionPeriodStatus.PAID);

        loginAsMember(member, RoleName.MEMBRE);
        assertThatThrownBy(() -> declare(period, 2500, "VOL-2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà déclaré ou validé");
    }

    @Test
    void participantStatusesReflectPartialAndFullPayments() {
        Member full = createMember(RoleName.MEMBRE, "statusfull");
        Member partial = createMember(RoleName.MEMBRE, "statuspartial");
        Member none = createMember(RoleName.MEMBRE, "statusnone");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(full);
        definitionService.addParticipant(period.contributionDefinitionId(), new MemberRefRequest(partial.getId()));
        definitionService.addParticipant(period.contributionDefinitionId(), new MemberRefRequest(none.getId()));
        Member treasurer = createMember(RoleName.TRESORIER, "tresostatus");

        loginAsMember(full, RoleName.MEMBRE);
        ContributionTransactionResponse txFull = declare(period, 50000, "ST-1");
        loginAsMember(partial, RoleName.MEMBRE);
        ContributionTransactionResponse txPartial = declare(period, 20000, "ST-2");
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(txFull.id());
        transactionService.validate(txPartial.id());

        // Lecture ouverte à un membre qui voit la cotisation (publique, active)
        loginAsMember(none, RoleName.MEMBRE);
        List<ParticipantPaymentStatusResponse> statuses =
                definitionService.listParticipantStatuses(period.contributionDefinitionId(), period.id());
        assertThat(statuses).hasSize(3);
        java.util.Map<java.util.UUID, ParticipantPaymentStatusResponse> byMember = statuses.stream()
                .collect(java.util.stream.Collectors.toMap(ParticipantPaymentStatusResponse::memberId, s -> s));
        assertThat(byMember.get(full.getId()).status()).isEqualTo(ContributionPeriodStatus.PAID);
        assertThat(byMember.get(full.getId()).remainingAmount()).isEqualByComparingTo("0");
        assertThat(byMember.get(partial.getId()).status()).isEqualTo(ContributionPeriodStatus.PARTIAL);
        assertThat(byMember.get(partial.getId()).validatedAmount()).isEqualByComparingTo("20000");
        assertThat(byMember.get(partial.getId()).remainingAmount()).isEqualByComparingTo("30000");
        assertThat(byMember.get(none.getId()).status()).isEqualTo(ContributionPeriodStatus.NOT_PAID);
        assertThat(byMember.get(none.getId()).dueAmount()).isEqualByComparingTo("50000");

        assertThatThrownBy(() -> definitionService.listParticipantStatuses(
                period.contributionDefinitionId(), java.util.UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void duplicateActiveDeclarationForSamePeriodIsRejected() {
        Member member = createMember(RoleName.MEMBRE, "payer4");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(member);

        loginAsMember(member, RoleName.MEMBRE);
        paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(50000), PaymentOperator.CASH,
                        "REF-004", LocalDate.of(2026, 10, 5), null));

        assertThatThrownBy(() -> paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(50000), PaymentOperator.CASH,
                        "REF-005", LocalDate.of(2026, 10, 6), null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void memberCannotValidateOwnPaymentEvenAsTreasurer() {
        Member treasurerMember = createMember(RoleName.TRESORIER, "selfvalidate1");
        loginAsMember(treasurerMember, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation Ration", null, new BigDecimal(2000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(def.id(), new MemberRefRequest(treasurerMember.getId()));
        definitionService.activate(def.id());
        Session session = sessionRepository.save(
                new Session("Novembre 2026", LocalDate.of(2026, 11, 1), null, null));
        ContributionPeriodResponse period = definitionService.addPeriod(def.id(),
                new ContributionPeriodRequest(session.getId(), null));

        ContributionTransactionResponse declared = paymentService.declare(def.id(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(2000), PaymentOperator.CASH,
                        "REF-006", LocalDate.of(2026, 11, 5), null));

        assertThatThrownBy(() -> transactionService.validate(declared.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectedPaymentCanBeRedeclared() {
        Member member = createMember(RoleName.MEMBRE, "payer5");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(member);
        Member treasurer = createMember(RoleName.TRESORIER, "treso5");

        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse first = paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(50000), PaymentOperator.CASH,
                        "REF-007", LocalDate.of(2026, 10, 5), null));

        loginAsMember(treasurer, RoleName.TRESORIER);
        ContributionTransactionResponse rejected = transactionService.reject(first.id());
        assertThat(rejected.status()).isEqualTo(ContributionTransactionStatus.REJECTED);

        loginAsMember(member, RoleName.MEMBRE);
        ContributionTransactionResponse second = paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(50000), PaymentOperator.CASH,
                        "REF-008", LocalDate.of(2026, 10, 6), null));
        assertThat(second.status()).isEqualTo(ContributionTransactionStatus.PENDING);
    }
}
