package cm.aekd.tontine.repayment;

import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.loan.LoanApprovalRequest;
import cm.aekd.tontine.loan.LoanPolicyRequest;
import cm.aekd.tontine.loan.LoanPolicyService;
import cm.aekd.tontine.loan.LoanRequest;
import cm.aekd.tontine.loan.LoanResponse;
import cm.aekd.tontine.loan.LoanService;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Remboursements déclarés par le membre puis validés par le trésorier (docs/decisions.md n° 38). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanRepaymentDeclarationTest {

    @Autowired private LoanPolicyService policyService;
    @Autowired private LoanService loanService;
    @Autowired private LoanRepaymentService repaymentService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Member treasurer;
    private Member borrower;
    private Member other;
    private UUID loanId;

    @BeforeEach
    void setUp() {
        Member admin = createMember(RoleName.ADMIN, "repdecl-admin");
        treasurer = createMember(RoleName.TRESORIER, "repdecl-treso");
        borrower = createMember(RoleName.MEMBRE, "repdecl-borrower");
        other = createMember(RoleName.MEMBRE, "repdecl-other");

        loginAs(admin, RoleName.ADMIN);
        policyService.create(new LoanPolicyRequest("Politique test", null, null, null, null, null, null, null, null));
        loginAs(borrower, RoleName.MEMBRE);
        LoanResponse requested = loanService.request(new LoanRequest(new BigDecimal(100000), 4, "Projet"));
        loginAs(treasurer, RoleName.TRESORIER);
        loanId = loanService.approve(requested.id(), new LoanApprovalRequest(new BigDecimal(100000), 4)).id();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Member createMember(RoleName roleName, String prefix) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        userRepository.save(user);
        return memberRepository.save(new Member(user, "Test " + prefix, LocalDate.of(2024, 1, 1)));
    }

    private void loginAs(Member member, RoleName role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                member.getUser().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private LoanRepaymentRequest request(long amount, String ref) {
        return new LoanRepaymentRequest(new BigDecimal(amount), LocalDate.now(), PaymentOperator.MTN_MOMO, ref);
    }

    @Test
    void declaredRepaymentOnlyCountsOnceValidated() {
        loginAs(borrower, RoleName.MEMBRE);
        LoanRepaymentResponse declared = repaymentService.declare(loanId, request(30000, "MOMO-1"));

        assertThat(declared.status()).isEqualTo(LoanRepaymentStatus.PENDING);
        LoanBalanceResponse pendingBalance = repaymentService.balance(loanId);
        assertThat(pendingBalance.remainingBalance()).isEqualByComparingTo("100000");
        assertThat(pendingBalance.pendingAmount()).isEqualByComparingTo("30000");
        assertThat(loanService.findById(loanId).status()).isEqualTo(LoanStatus.APPROVED);

        loginAs(treasurer, RoleName.TRESORIER);
        assertThat(repaymentService.findPending()).extracting(LoanRepaymentResponse::id).contains(declared.id());
        LoanRepaymentResponse validated = repaymentService.validate(declared.id());

        assertThat(validated.status()).isEqualTo(LoanRepaymentStatus.VALIDATED);
        assertThat(validated.validatedByEmail()).isEqualTo("repdecl-treso@test.aekd.cm");
        LoanBalanceResponse balance = repaymentService.balance(loanId);
        assertThat(balance.remainingBalance()).isEqualByComparingTo("70000");
        assertThat(balance.pendingAmount()).isEqualByComparingTo("0");
        assertThat(loanService.findById(loanId).status()).isEqualTo(LoanStatus.IN_PROGRESS);
    }

    @Test
    void fullRepaymentByMemberMarksLoanRepaidAfterValidation() {
        loginAs(borrower, RoleName.MEMBRE);
        LoanRepaymentResponse declared = repaymentService.declare(loanId, request(100000, "MOMO-ALL"));

        loginAs(treasurer, RoleName.TRESORIER);
        repaymentService.validate(declared.id());

        assertThat(loanService.findById(loanId).status()).isEqualTo(LoanStatus.REPAID);
        assertThat(repaymentService.balance(loanId).remainingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void declarationsCannotExceedRemainingMinusPending() {
        loginAs(borrower, RoleName.MEMBRE);
        repaymentService.declare(loanId, request(80000, "MOMO-A"));

        assertThatThrownBy(() -> repaymentService.declare(loanId, request(30000, "MOMO-B")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dépasse");
        assertThat(repaymentService.declare(loanId, request(20000, "MOMO-C")).status())
                .isEqualTo(LoanRepaymentStatus.PENDING);
    }

    @Test
    void rejectedRepaymentDoesNotChangeBalance() {
        loginAs(borrower, RoleName.MEMBRE);
        LoanRepaymentResponse declared = repaymentService.declare(loanId, request(40000, "MOMO-R"));

        loginAs(treasurer, RoleName.TRESORIER);
        assertThat(repaymentService.reject(declared.id()).status()).isEqualTo(LoanRepaymentStatus.REJECTED);
        assertThat(repaymentService.balance(loanId).remainingBalance()).isEqualByComparingTo("100000");
        assertThatThrownBy(() -> repaymentService.validate(declared.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà été traité");
    }

    @Test
    void onlyTheBorrowerCanDeclareAndNobodyDecidesOnOwnLoan() {
        loginAs(other, RoleName.MEMBRE);
        assertThatThrownBy(() -> repaymentService.declare(loanId, request(10000, "X")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("introuvable");

        loginAs(borrower, RoleName.MEMBRE);
        LoanRepaymentResponse declared = repaymentService.declare(loanId, request(10000, "MOMO-SELF"));
        // Le bénéficiaire du prêt ne peut pas valider son propre remboursement, même avec le rôle trésorier.
        loginAs(borrower, RoleName.TRESORIER);
        assertThatThrownBy(() -> repaymentService.validate(declared.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("propre prêt");
    }

    @Test
    void borrowerAttachesProofWhilePendingOnly() {
        loginAs(borrower, RoleName.MEMBRE);
        LoanRepaymentResponse declared = repaymentService.declare(loanId, request(10000, "MOMO-P"));
        MockMultipartFile proof = new MockMultipartFile("file", "recu.png", "image/png", new byte[]{1, 2, 3});

        assertThat(repaymentService.uploadProof(declared.id(), proof).hasProof()).isTrue();
        assertThat(repaymentService.downloadProof(declared.id()).filename()).isEqualTo("recu.png");

        loginAs(other, RoleName.MEMBRE);
        assertThatThrownBy(() -> repaymentService.uploadProof(declared.id(), proof))
                .isInstanceOf(ResponseStatusException.class);

        loginAs(treasurer, RoleName.TRESORIER);
        repaymentService.validate(declared.id());
        loginAs(borrower, RoleName.MEMBRE);
        assertThatThrownBy(() -> repaymentService.uploadProof(declared.id(), proof))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("déjà été traité");
    }
}
