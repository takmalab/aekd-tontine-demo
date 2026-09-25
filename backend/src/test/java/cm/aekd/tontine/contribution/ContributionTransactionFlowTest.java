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
                new Session("Octobre 2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
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
    void fixedAmountMismatchIsRejected() {
        Member member = createMember(RoleName.MEMBRE, "payer3");
        ContributionPeriodResponse period = setupActiveDefinitionWithParticipant(member);

        loginAsMember(member, RoleName.MEMBRE);
        assertThatThrownBy(() -> paymentService.declare(period.contributionDefinitionId(),
                new ContributionTransactionRequest(period.id(), new BigDecimal(10000), PaymentOperator.CASH,
                        "REF-003", LocalDate.of(2026, 10, 5), null)))
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
                new Session("Novembre 2026", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));
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
