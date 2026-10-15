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
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanApprovalRequest;
import cm.aekd.tontine.loan.LoanPolicyRequest;
import cm.aekd.tontine.loan.LoanPolicyService;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanRequest;
import cm.aekd.tontine.loan.LoanResponse;
import cm.aekd.tontine.loan.LoanService;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.payout.SessionPayoutDtos.DeductionLine;
import cm.aekd.tontine.payout.SessionPayoutDtos.DeductionOption;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutRequest;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutResponse;
import cm.aekd.tontine.repayment.LoanRepaymentService;
import cm.aekd.tontine.repayment.RepaymentSource;
import cm.aekd.tontine.repayment.SavingsDeductionRequest;
import cm.aekd.tontine.savings.SavingsDepositRepository;
import cm.aekd.tontine.savings.SavingsDtos.EntrySource;
import cm.aekd.tontine.savings.SavingsService;
import cm.aekd.tontine.savings.TestSavings;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.SessionRequest;
import cm.aekd.tontine.session.SessionResponse;
import cm.aekd.tontine.session.SessionService;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Recouvrement d'un prêt (docs/decisions.md n° 53) : retenue sur la cagnotte de
 * l'emprunteur, puis de l'avaliste une fois l'épargne de l'emprunteur épuisée ;
 * prélèvement sur l'épargne d'un prêt en retard.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanRecoveryTest {

    @Autowired private SessionPayoutService payoutService;
    @Autowired private SessionService sessionService;
    @Autowired private ContributionDefinitionService definitionService;
    @Autowired private PaymentService paymentService;
    @Autowired private ContributionTransactionService transactionService;
    @Autowired private LoanPolicyService policyService;
    @Autowired private LoanService loanService;
    @Autowired private LoanRepository loanRepository;
    @Autowired private LoanRepaymentService repaymentService;
    @Autowired private SavingsService savingsService;
    @Autowired private SavingsDepositRepository depositRepository;
    @Autowired private FundService fundService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void borrowerFirstThenGuarantorFromPayoutsAndSavings() {
        Member admin = createMember(RoleName.ADMIN, "recov-admin");
        Member treasurer = createMember(RoleName.TRESORIER, "recov-treso");
        Member borrower = createMember(RoleName.MEMBRE, "recov-borrower");
        Member guarantor = createMember(RoleName.MEMBRE, "recov-guarantor");
        Member saver = createMember(RoleName.MEMBRE, "recov-saver");
        TestSavings.seedValidated(depositRepository, saver, new BigDecimal(1000000));
        TestSavings.seedValidated(depositRepository, borrower, new BigDecimal(3000));

        // Séance : emprunteur et avaliste bénéficiaires, 10 000 chacun.
        loginAs(treasurer, RoleName.TRESORIER);
        SessionResponse session = sessionService.create(new SessionRequest("Séance recouvrement", LocalDate.now(),
                "Douala", borrower.getId(), List.of(borrower.getId(), guarantor.getId())));
        ContributionDefinitionResponse mandatory = definitionService.create(new ContributionDefinitionRequest(
                "Cotisation 10 000", null, new BigDecimal(10000), AmountMode.FIXED, ContributionFrequency.MONTHLY,
                true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        definitionService.addParticipant(mandatory.id(), new MemberRefRequest(borrower.getId()));
        definitionService.addParticipant(mandatory.id(), new MemberRefRequest(guarantor.getId()));
        definitionService.activate(mandatory.id());
        ContributionPeriodResponse period = definitionService.addPeriod(mandatory.id(),
                new ContributionPeriodRequest(session.id(), null));
        payAndValidate(treasurer, borrower, mandatory, period);
        payAndValidate(treasurer, guarantor, mandatory, period);

        // Prêt de 20 000 garanti, approuvé.
        loginAs(admin, RoleName.ADMIN);
        policyService.create(new LoanPolicyRequest("Simple", null, null, null, null, null, null, null, null));
        loginAs(borrower, RoleName.MEMBRE);
        LoanResponse loan = loanService.request(new LoanRequest(new BigDecimal(20000), 2, "Motif", guarantor.getId()));
        loginAs(guarantor, RoleName.MEMBRE);
        loanService.answerGuarantee(loan.id(), true);
        loginAs(treasurer, RoleName.TRESORIER);
        loanService.approve(loan.id(), new LoanApprovalRequest(new BigDecimal(20000), 2));

        // Cagnotte de l'emprunteur : 8 000 retenus, 2 000 remis.
        List<DeductionOption> borrowerOptions = payoutService.deductionOptions(session.id(), borrower.getId());
        assertThat(borrowerOptions).singleElement().satisfies(o -> {
            assertThat(o.role()).isEqualTo("BORROWER");
            assertThat(o.outstanding()).isEqualByComparingTo("20000");
            assertThat(o.blockedReason()).isNull();
        });
        PayoutResponse payout = payoutService.record(session.id(), payout(borrower, 10000,
                List.of(new DeductionLine(loan.id(), new BigDecimal(8000)))));
        assertThat(payout.deductedAmount()).isEqualByComparingTo("8000");
        Loan saved = loanRepository.findById(loan.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LoanStatus.IN_PROGRESS);
        assertThat(repaymentService.outstanding(saved)).isEqualByComparingTo("12000");
        assertThat(repaymentService.findByLoan(loan.id()))
                .anyMatch(r -> r.source() == RepaymentSource.PAYOUT_DEDUCTION && r.payerMemberId().equals(borrower.getId()));

        // L'emprunteur a encore 3 000 d'épargne : l'avaliste n'est pas encore prélevé.
        DeductionOption guarantorOption = payoutService.deductionOptions(session.id(), guarantor.getId()).get(0);
        assertThat(guarantorOption.role()).isEqualTo("GUARANTOR");
        assertThat(guarantorOption.blockedReason()).contains("l'emprunteur d'abord");
        assertThatThrownBy(() -> payoutService.record(session.id(), payout(guarantor, 10000,
                List.of(new DeductionLine(loan.id(), new BigDecimal(5000))))))
                .hasMessageContaining("prélevez-la d'abord");

        // Épargne : seulement une fois le prêt en retard.
        assertThatThrownBy(() -> repaymentService.deductFromSavings(loan.id(),
                new SavingsDeductionRequest(SavingsDeductionRequest.Payer.BORROWER, new BigDecimal(3000))))
                .hasMessageContaining("pas en retard");
        saved.setDecisionDate(Instant.now().minus(100, ChronoUnit.DAYS));
        loanRepository.save(saved);
        repaymentService.deductFromSavings(loan.id(),
                new SavingsDeductionRequest(SavingsDeductionRequest.Payer.BORROWER, new BigDecimal(3000)));
        assertThat(fundService.individualSavingsOf(borrower.getId())).isEqualByComparingTo("0");
        assertThat(savingsService.ofMember(borrower.getId()).entries())
                .anyMatch(e -> e.source() == EntrySource.LOAN_DEDUCTION && e.amount().compareTo(new BigDecimal(-3000)) == 0);

        // Épargne de l'emprunteur épuisée : la cagnotte de l'avaliste couvre le reste (9 000).
        payoutService.record(session.id(), payout(guarantor, 10000,
                List.of(new DeductionLine(loan.id(), new BigDecimal(9000)))));
        assertThat(loanRepository.findById(loan.id()).orElseThrow().getStatus()).isEqualTo(LoanStatus.REPAID);
    }

    private PayoutRequest payout(Member beneficiary, long amount, List<DeductionLine> deductions) {
        return new PayoutRequest(beneficiary.getId(), BigDecimal.valueOf(amount), LocalDate.now(),
                PaymentOperator.CASH, null, null, deductions);
    }

    private void payAndValidate(Member treasurer, Member payer, ContributionDefinitionResponse definition,
                                ContributionPeriodResponse period) {
        loginAs(payer, RoleName.MEMBRE);
        var tx = paymentService.declare(definition.id(), new ContributionTransactionRequest(
                period.id(), new BigDecimal(10000), PaymentOperator.CASH, "REF-" + payer.getId(), LocalDate.now(), null));
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
