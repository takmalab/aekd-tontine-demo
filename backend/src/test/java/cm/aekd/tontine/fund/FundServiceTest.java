package cm.aekd.tontine.fund;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinitionRequest;
import cm.aekd.tontine.contribution.ContributionDefinitionResponse;
import cm.aekd.tontine.contribution.ContributionDefinitionService;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionPeriodRequest;
import cm.aekd.tontine.contribution.ContributionPeriodResponse;
import cm.aekd.tontine.contribution.ContributionTransactionResponse;
import cm.aekd.tontine.contribution.ContributionTransactionService;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.MemberRefRequest;
import cm.aekd.tontine.contribution.ContributionTransactionRequest;
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
class FundServiceTest {

    @Autowired
    private ContributionDefinitionService definitionService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ContributionTransactionService transactionService;

    @Autowired
    private FundService fundService;

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

    private ContributionPeriodResponse setupActiveDefinition(Member treasurer, Member participant,
                                                              FundDestination destination, BigDecimal amount) {
        loginAsMember(treasurer, RoleName.TRESORIER);
        ContributionDefinitionResponse def = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation " + destination, null, amount, AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, destination));
        definitionService.addParticipant(def.id(), new MemberRefRequest(participant.getId()));
        definitionService.activate(def.id());
        Session session = sessionRepository.save(
                new Session("Session " + destination, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
        return definitionService.addPeriod(def.id(), new ContributionPeriodRequest(session.getId(), null));
    }

    @Test
    void onlyValidatedTransactionsCountAndFundsAreNeverMixed() {
        Member treasurer = createMember(RoleName.TRESORIER, "fundtreso1");
        Member memberA = createMember(RoleName.MEMBRE, "funda1");
        Member memberB = createMember(RoleName.MEMBRE, "fundb1");

        // Fonds commun : A paie 50 000 (validé), A paie encore 10 000 (reste PENDING, ne doit pas compter)
        ContributionPeriodResponse tontinePeriod1 = setupActiveDefinition(treasurer, memberA,
                FundDestination.TONTINE_FUND, new BigDecimal(50000));
        loginAsMember(memberA, RoleName.MEMBRE);
        ContributionTransactionResponse validatedTontine = paymentService.declare(tontinePeriod1.contributionDefinitionId(),
                new ContributionTransactionRequest(tontinePeriod1.id(), new BigDecimal(50000), PaymentOperator.CASH,
                        "REF-F1", LocalDate.of(2026, 10, 5), null));
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(validatedTontine.id());

        ContributionPeriodResponse tontinePeriod2 = setupActiveDefinition(treasurer, memberB,
                FundDestination.TONTINE_FUND, new BigDecimal(10000));
        loginAsMember(memberB, RoleName.MEMBRE);
        paymentService.declare(tontinePeriod2.contributionDefinitionId(),
                new ContributionTransactionRequest(tontinePeriod2.id(), new BigDecimal(10000), PaymentOperator.CASH,
                        "REF-F2", LocalDate.of(2026, 10, 5), null));
        // volontairement laissé PENDING (pas de validate)

        // Un paiement REJECTED ne doit pas compter non plus
        ContributionPeriodResponse tontinePeriod3 = setupActiveDefinition(treasurer, memberA,
                FundDestination.TONTINE_FUND, new BigDecimal(2000));
        loginAsMember(memberA, RoleName.MEMBRE);
        ContributionTransactionResponse rejected = paymentService.declare(tontinePeriod3.contributionDefinitionId(),
                new ContributionTransactionRequest(tontinePeriod3.id(), new BigDecimal(2000), PaymentOperator.CASH,
                        "REF-F3", LocalDate.of(2026, 10, 5), null));
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.reject(rejected.id());

        // Épargne individuelle : A verse 20 000 (validé)
        ContributionPeriodResponse savingsPeriod = setupActiveDefinition(treasurer, memberA,
                FundDestination.INDIVIDUAL_SAVINGS, new BigDecimal(20000));
        loginAsMember(memberA, RoleName.MEMBRE);
        ContributionTransactionResponse savingsTx = paymentService.declare(savingsPeriod.contributionDefinitionId(),
                new ContributionTransactionRequest(savingsPeriod.id(), new BigDecimal(20000), PaymentOperator.CASH,
                        "REF-F4", LocalDate.of(2026, 10, 5), null));
        loginAsMember(treasurer, RoleName.TRESORIER);
        transactionService.validate(savingsTx.id());

        assertThat(fundService.tontineFundBalance()).isEqualByComparingTo(new BigDecimal(50000));
        assertThat(fundService.totalIndividualSavings()).isEqualByComparingTo(new BigDecimal(20000));
        assertThat(fundService.individualSavingsOf(memberA.getId())).isEqualByComparingTo(new BigDecimal(20000));
        assertThat(fundService.individualSavingsOf(memberB.getId())).isEqualByComparingTo(BigDecimal.ZERO);

        loginAsMember(memberA, RoleName.MEMBRE);
        assertThat(fundService.myIndividualSavings()).isEqualByComparingTo(new BigDecimal(20000));
    }

    @Test
    void balancesAreZeroWhenNoValidatedTransactions() {
        Member member = createMember(RoleName.MEMBRE, "fundempty1");
        assertThat(fundService.tontineFundBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fundService.individualSavingsOf(member.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unknownMemberSavingsLookupFails() {
        assertThatThrownBy(() -> fundService.individualSavingsOf(java.util.UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
