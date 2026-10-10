package cm.aekd.tontine.payout;

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
import cm.aekd.tontine.fund.FundService;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.payout.SessionPayoutDtos.BeneficiaryShare;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutOverview;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutRequest;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.SessionRequest;
import cm.aekd.tontine.session.SessionResponse;
import cm.aekd.tontine.session.SessionService;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/** Remise de la cagnotte de la séance (docs/decisions.md n° 46). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionPayoutServiceTest {

    @Autowired private SessionPayoutService payoutService;
    @Autowired private SessionService sessionService;
    @Autowired private ContributionDefinitionService definitionService;
    @Autowired private PaymentService paymentService;
    @Autowired private ContributionTransactionService transactionService;
    @Autowired private FundService fundService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Member admin;
    private Member treasurer;
    private Member memberA;
    private Member memberB;
    private SessionResponse session;

    @BeforeEach
    void setUp() {
        admin = createMember(RoleName.ADMIN, "payout-admin");
        treasurer = createMember(RoleName.TRESORIER, "payout-treso");
        memberA = createMember(RoleName.MEMBRE, "payout-a");
        memberB = createMember(RoleName.MEMBRE, "payout-b");

        // Trois bénéficiaires, dont le trésorier : 20 000 / 3 ne tombe pas juste.
        loginAs(treasurer, RoleName.TRESORIER);
        session = sessionService.create(new SessionRequest("Séance cagnotte", LocalDate.now(), "Douala",
                memberA.getId(), List.of(memberA.getId(), memberB.getId(), treasurer.getId())));

        ContributionDefinitionResponse mandatory = activeDefinition("Cotisation 10 000", new BigDecimal(10000),
                AmountMode.FIXED, true, FundDestination.TONTINE_FUND);
        ContributionDefinitionResponse savings = activeDefinition("Banque / Épargne", null,
                AmountMode.VOLUNTARY, false, FundDestination.INDIVIDUAL_SAVINGS);
        ContributionPeriodResponse mandatoryPeriod = definitionService.addPeriod(mandatory.id(),
                new ContributionPeriodRequest(session.id(), null));
        ContributionPeriodResponse savingsPeriod = definitionService.addPeriod(savings.id(),
                new ContributionPeriodRequest(session.id(), null));

        payAndValidate(memberA, mandatory, mandatoryPeriod, new BigDecimal(10000));
        payAndValidate(memberB, mandatory, mandatoryPeriod, new BigDecimal(10000));
        // L'épargne individuelle n'est jamais remise au bénéficiaire.
        payAndValidate(memberA, savings, savingsPeriod, new BigDecimal(5000));
        loginAs(treasurer, RoleName.TRESORIER);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overviewSplitsValidatedMandatoryFundPaymentsEqually() {
        PayoutOverview overview = payoutService.overview(session.id());

        assertThat(overview.collected()).isEqualByComparingTo(new BigDecimal(20000));
        assertThat(overview.contributions()).hasSize(1);
        assertThat(overview.beneficiaries()).hasSize(3)
                .allSatisfy(b -> assertThat(b.share()).isEqualByComparingTo(new BigDecimal(6666)));
        assertThat(overview.unallocated()).isEqualByComparingTo(new BigDecimal(2));
        assertThat(overview.remaining()).isEqualByComparingTo(new BigDecimal(20000));
    }

    @Test
    void recordingAPayoutReducesTheBeneficiaryDueAndTheTontineFund() {
        BigDecimal fundBefore = fundService.tontineFundBalance();

        payoutService.record(session.id(), request(memberB, 6000));

        PayoutOverview overview = payoutService.overview(session.id());
        BeneficiaryShare shareB = shareOf(overview, memberB);
        assertThat(shareB.remitted()).isEqualByComparingTo(new BigDecimal(6000));
        assertThat(shareB.due()).isEqualByComparingTo(new BigDecimal(666));
        assertThat(overview.remitted()).isEqualByComparingTo(new BigDecimal(6000));
        assertThat(fundService.tontineFundBalance()).isEqualByComparingTo(fundBefore.subtract(new BigDecimal(6000)));
        assertThat(fundService.tontineFundRemitted()).isEqualByComparingTo(new BigDecimal(6000));

        assertThatThrownBy(() -> payoutService.record(session.id(), request(memberB, 667)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dépasse la part restant");
        payoutService.record(session.id(), request(memberB, 666));
        assertThatThrownBy(() -> payoutService.record(session.id(), request(memberB, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("toute sa part");
    }

    @Test
    void onlySessionBeneficiariesCanReceiveAPayout() {
        assertThatThrownBy(() -> payoutService.record(session.id(), request(admin, 1000)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pas bénéficiaire");
    }

    @Test
    void treasurerCannotRecordAPayoutForHimself() {
        assertThatThrownBy(() -> payoutService.record(session.id(), request(treasurer, 1000)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("à votre profit");

        loginAs(admin, RoleName.ADMIN);
        payoutService.record(session.id(), request(treasurer, 1000));
        assertThat(shareOf(payoutService.overview(session.id()), treasurer).remitted())
                .isEqualByComparingTo(new BigDecimal(1000));
    }

    @Test
    void sessionAndPaidBeneficiaryCannotBeRemovedAfterAPayout() {
        payoutService.record(session.id(), request(memberB, 1000));

        assertThatThrownBy(() -> sessionService.update(session.id(), new SessionRequest("Séance cagnotte",
                LocalDate.now(), "Douala", memberA.getId(), List.of(memberA.getId(), treasurer.getId()))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("remise de la cagnotte");
        assertThatThrownBy(() -> sessionService.delete(session.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private PayoutRequest request(Member beneficiary, long amount) {
        return new PayoutRequest(beneficiary.getId(), BigDecimal.valueOf(amount), LocalDate.now(),
                PaymentOperator.CASH, null, null);
    }

    private static BeneficiaryShare shareOf(PayoutOverview overview, Member member) {
        return overview.beneficiaries().stream()
                .filter(b -> b.memberId().equals(member.getId()))
                .findFirst().orElseThrow();
    }

    private ContributionDefinitionResponse activeDefinition(String name, BigDecimal amount, AmountMode mode,
                                                             boolean mandatory, FundDestination destination) {
        ContributionDefinitionResponse definition = definitionService.create(new ContributionDefinitionRequest(
                name, null, amount, mode, ContributionFrequency.MONTHLY, mandatory, Visibility.PUBLIC, destination));
        for (Member member : List.of(memberA, memberB)) {
            definitionService.addParticipant(definition.id(), new MemberRefRequest(member.getId()));
        }
        return definitionService.activate(definition.id());
    }

    private void payAndValidate(Member payer, ContributionDefinitionResponse definition,
                                ContributionPeriodResponse period, BigDecimal amount) {
        loginAs(payer, RoleName.MEMBRE);
        var tx = paymentService.declare(definition.id(), new ContributionTransactionRequest(
                period.id(), amount, PaymentOperator.CASH, "REF-" + payer.getId(), LocalDate.now(), null));
        loginAs(treasurer, RoleName.TRESORIER);
        transactionService.validate(tx.id());
    }

    private Member createMember(RoleName roleName, String prefix) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        return memberRepository.save(new Member(user, "Test " + prefix, LocalDate.now()));
    }

    private void loginAs(Member member, RoleName roleName) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                member.getUser().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + roleName))));
    }
}
