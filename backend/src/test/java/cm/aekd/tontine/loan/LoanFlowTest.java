package cm.aekd.tontine.loan;

import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.repayment.LoanBalanceResponse;
import cm.aekd.tontine.repayment.LoanRepaymentRequest;
import cm.aekd.tontine.repayment.LoanRepaymentResponse;
import cm.aekd.tontine.repayment.LoanRepaymentService;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@Transactional
class LoanFlowTest {

    @Autowired
    private LoanPolicyService policyService;

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepaymentService repaymentService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Member createMember(RoleName roleName, String emailPrefix, LocalDate joinDate) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(emailPrefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        Member member = new Member(user, "Test " + emailPrefix, joinDate);
        return memberRepository.save(member);
    }

    private void loginAsMember(Member member, RoleName roleName) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getUser().getEmail(), null, authorities));
    }

    @Test
    void fullDemoFlowRequestApproveRepay() {
        Member admin = createMember(RoleName.ADMIN, "loanadmin2", LocalDate.now());
        Member treasurer = createMember(RoleName.TRESORIER, "loantreso2", LocalDate.now());
        Member member = createMember(RoleName.MEMBRE, "loanmember2", LocalDate.of(2024, 1, 1));

        loginAsMember(admin, RoleName.ADMIN);
        LoanPolicyResponse policy = policyService.create(new LoanPolicyRequest(
                "Politique standard", null, new BigDecimal(50000), new BigDecimal(300000), 12,
                new BigDecimal("5.00"), null, 6, 1));
        assertThat(policy.active()).isTrue();

        loginAsMember(member, RoleName.MEMBRE);
        LoanResponse requested = loanService.request(new LoanRequest(new BigDecimal(300000), 6, "Achat matériel"));
        assertThat(requested.status()).isEqualTo(LoanStatus.REQUESTED);
        assertThat(requested.ruleEvaluations()).isNotEmpty();
        assertThat(requested.ruleEvaluations()).allMatch(LoanRuleEvaluationResponse::respected);

        loginAsMember(treasurer, RoleName.TRESORIER);
        LoanResponse approved = loanService.approve(requested.id(),
                new LoanApprovalRequest(new BigDecimal(300000), 6));
        assertThat(approved.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(approved.approvedAmount()).isEqualByComparingTo(new BigDecimal(300000));

        LoanRepaymentResponse repayment1 = repaymentService.record(approved.id(),
                new LoanRepaymentRequest(new BigDecimal(50000), LocalDate.now(), PaymentOperator.CASH, "REMB-1"));
        assertThat(repayment1.amount()).isEqualByComparingTo(new BigDecimal(50000));

        LoanBalanceResponse balanceAfterFirst = repaymentService.balance(approved.id());
        assertThat(balanceAfterFirst.remainingBalance()).isEqualByComparingTo(new BigDecimal(250000));

        LoanResponse afterFirstRepayment = loanService.findById(approved.id());
        assertThat(afterFirstRepayment.status()).isEqualTo(LoanStatus.IN_PROGRESS);

        repaymentService.record(approved.id(),
                new LoanRepaymentRequest(new BigDecimal(250000), LocalDate.now(), PaymentOperator.CASH, "REMB-2"));

        LoanResponse afterFullRepayment = loanService.findById(approved.id());
        assertThat(afterFullRepayment.status()).isEqualTo(LoanStatus.REPAID);

        LoanBalanceResponse finalBalance = repaymentService.balance(approved.id());
        assertThat(finalBalance.remainingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void repaymentExceedingBalanceIsRejected() {
        Member admin = createMember(RoleName.ADMIN, "loanadmin3", LocalDate.now());
        Member treasurer = createMember(RoleName.TRESORIER, "loantreso3", LocalDate.now());
        Member member = createMember(RoleName.MEMBRE, "loanmember3", LocalDate.of(2024, 1, 1));

        loginAsMember(admin, RoleName.ADMIN);
        policyService.create(new LoanPolicyRequest("Politique simple", null, null, null, null, null, null, null, null));

        loginAsMember(member, RoleName.MEMBRE);
        LoanResponse requested = loanService.request(new LoanRequest(new BigDecimal(100000), 3, "Motif"));

        loginAsMember(treasurer, RoleName.TRESORIER);
        LoanResponse approved = loanService.approve(requested.id(), new LoanApprovalRequest(new BigDecimal(100000), 3));

        assertThatThrownBy(() -> repaymentService.record(approved.id(),
                new LoanRepaymentRequest(new BigDecimal(150000), LocalDate.now(), PaymentOperator.CASH, "REMB-X")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void ruleEvaluationExplainsWhyRequestFailsButStillAllowsApproval() {
        Member admin = createMember(RoleName.ADMIN, "loanadmin4", LocalDate.now());
        Member treasurer = createMember(RoleName.TRESORIER, "loantreso4", LocalDate.now());
        // Membre très récent : ne respectera pas l'ancienneté minimale
        Member member = createMember(RoleName.MEMBRE, "loanmember4", LocalDate.now());

        loginAsMember(admin, RoleName.ADMIN);
        policyService.create(new LoanPolicyRequest(
                "Politique stricte", null, null, new BigDecimal(100000), null, null, null, 24, null));

        loginAsMember(member, RoleName.MEMBRE);
        LoanResponse requested = loanService.request(new LoanRequest(new BigDecimal(50000), 3, "Motif"));

        LoanRuleEvaluationResponse seniorityRule = requested.ruleEvaluations().stream()
                .filter(e -> e.ruleName().equals("ANCIENNETE_MINIMALE"))
                .findFirst().orElseThrow();
        assertThat(seniorityRule.respected()).isFalse();

        // Le trésorier peut quand même approuver malgré la règle non respectée
        // (aucun rejet automatique, docs/decisions.md ne tranche pas ce point pour les prêts)
        loginAsMember(treasurer, RoleName.TRESORIER);
        LoanResponse approved = loanService.approve(requested.id(), new LoanApprovalRequest(new BigDecimal(50000), 3));
        assertThat(approved.status()).isEqualTo(LoanStatus.APPROVED);
    }

    @Test
    void memberCannotRequestWithoutActivePolicy() {
        Member member = createMember(RoleName.MEMBRE, "loanmember5", LocalDate.now());
        loginAsMember(member, RoleName.MEMBRE);
        assertThatThrownBy(() -> loanService.request(new LoanRequest(new BigDecimal(10000), 1, "Motif")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void outsiderCannotViewSomeoneElsesLoan() {
        Member admin = createMember(RoleName.ADMIN, "loanadmin6", LocalDate.now());
        Member member = createMember(RoleName.MEMBRE, "loanmember6", LocalDate.now());
        Member outsider = createMember(RoleName.MEMBRE, "loanoutsider6", LocalDate.now());

        loginAsMember(admin, RoleName.ADMIN);
        policyService.create(new LoanPolicyRequest("Politique simple", null, null, null, null, null, null, null, null));

        loginAsMember(member, RoleName.MEMBRE);
        LoanResponse requested = loanService.request(new LoanRequest(new BigDecimal(20000), 2, "Motif"));

        loginAsMember(outsider, RoleName.MEMBRE);
        assertThatThrownBy(() -> loanService.findById(requested.id()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cannotApproveTwice() {
        Member admin = createMember(RoleName.ADMIN, "loanadmin7", LocalDate.now());
        Member treasurer = createMember(RoleName.TRESORIER, "loantreso7", LocalDate.now());
        Member member = createMember(RoleName.MEMBRE, "loanmember7", LocalDate.now());

        loginAsMember(admin, RoleName.ADMIN);
        policyService.create(new LoanPolicyRequest("Politique simple", null, null, null, null, null, null, null, null));

        loginAsMember(member, RoleName.MEMBRE);
        LoanResponse requested = loanService.request(new LoanRequest(new BigDecimal(20000), 2, "Motif"));

        loginAsMember(treasurer, RoleName.TRESORIER);
        loanService.approve(requested.id(), new LoanApprovalRequest(new BigDecimal(20000), 2));

        assertThatThrownBy(() -> loanService.approve(requested.id(), new LoanApprovalRequest(new BigDecimal(20000), 2)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
