package cm.aekd.tontine.config;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinitionRequest;
import cm.aekd.tontine.contribution.ContributionDefinitionResponse;
import cm.aekd.tontine.contribution.ContributionDefinitionService;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionPeriodRequest;
import cm.aekd.tontine.contribution.ContributionPeriodResponse;
import cm.aekd.tontine.contribution.ContributionTransactionRequest;
import cm.aekd.tontine.contribution.ContributionTransactionResponse;
import cm.aekd.tontine.contribution.ContributionTransactionService;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.MemberRefRequest;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.contribution.PaymentService;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.loan.LoanApprovalRequest;
import cm.aekd.tontine.loan.LoanPolicyRequest;
import cm.aekd.tontine.loan.LoanPolicyService;
import cm.aekd.tontine.loan.LoanRequest;
import cm.aekd.tontine.loan.LoanResponse;
import cm.aekd.tontine.loan.LoanService;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.repayment.LoanRepaymentRequest;
import cm.aekd.tontine.repayment.LoanRepaymentService;
import cm.aekd.tontine.sanction.SanctionRuleRequest;
import cm.aekd.tontine.sanction.SanctionRuleService;
import cm.aekd.tontine.sanction.SanctionType;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.SessionRequest;
import cm.aekd.tontine.session.SessionResponse;
import cm.aekd.tontine.session.SessionService;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Données de démonstration (CLAUDE.md §32). N'est jamais exécuté par
 * défaut : uniquement avec le profil Spring "demo"
 * ({@code SPRING_PROFILES_ACTIVE=demo}), pour ne jamais faire fuiter ces
 * identifiants dans un environnement réel. Idempotent : si
 * "admin@aekd.cm" existe déjà, rien n'est reseedé.
 *
 * "Voir bébé" est volontairement absente : c'est la seule cotisation
 * connue dont le montant ET la destination des fonds restent "à valider"
 * (§9.4) — impossible de la créer sans inventer une réponse à l'une des
 * deux. "Banque / Épargne" est incluse : seule sa destination est
 * tranchée (épargne individuelle) ; son montant reste libre (VOLUNTARY).
 *
 * Passe par la couche service (pas des insertions SQL) pour que toutes
 * les validations métier s'appliquent et que le journal d'audit
 * s'alimente naturellement, comme une vraie utilisation de l'application.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD_ADMIN = "Admin@123";
    private static final String DEFAULT_PASSWORD_TRESORIER = "Tresorier@123";
    private static final String DEFAULT_PASSWORD_MEMBRE = "Membre@123";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final ContributionDefinitionService definitionService;
    private final PaymentService paymentService;
    private final ContributionTransactionService transactionService;
    private final SanctionRuleService sanctionRuleService;
    private final LoanPolicyService loanPolicyService;
    private final LoanService loanService;
    private final LoanRepaymentService loanRepaymentService;

    public DemoDataSeeder(UserRepository userRepository, RoleRepository roleRepository,
                           MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                           SessionService sessionService, ContributionDefinitionService definitionService,
                           PaymentService paymentService, ContributionTransactionService transactionService,
                           SanctionRuleService sanctionRuleService, LoanPolicyService loanPolicyService,
                           LoanService loanService, LoanRepaymentService loanRepaymentService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.definitionService = definitionService;
        this.paymentService = paymentService;
        this.transactionService = transactionService;
        this.sanctionRuleService = sanctionRuleService;
        this.loanPolicyService = loanPolicyService;
        this.loanService = loanService;
        this.loanRepaymentService = loanRepaymentService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("admin@aekd.cm")) {
            return;
        }

        try {
            seed();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seed() {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        Role tresorierRole = roleRepository.findByName(RoleName.TRESORIER).orElseThrow();
        Role membreRole = roleRepository.findByName(RoleName.MEMBRE).orElseThrow();

        User admin = createUser("admin@aekd.cm", DEFAULT_PASSWORD_ADMIN, adminRole);
        User tresorier = createUser("tresorier@aekd.cm", DEFAULT_PASSWORD_TRESORIER, tresorierRole);

        Member jean = createMemberAccount("jean.mbarga@aekd.cm", "Jean Mbarga", LocalDate.of(2024, 1, 15), membreRole);
        Member marie = createMemberAccount("marie.ngobikoro@aekd.cm", "Marie Ngo Bikoro", LocalDate.of(2024, 6, 10), membreRole);
        Member paul = createMemberAccount("paul.etoundi@aekd.cm", "Paul Etoundi", LocalDate.of(2025, 1, 20), membreRole);
        Member sylvie = createMemberAccount("sylvie.abena@aekd.cm", "Sylvie Abena", LocalDate.of(2025, 8, 5), membreRole);
        Member robert = createMemberAccount("robert.fouda@aekd.cm", "Robert Fouda", LocalDate.of(2026, 6, 1), membreRole);
        List<Member> allMembers = List.of(jean, marie, paul, sylvie, robert);

        loginAs(tresorier);
        SessionResponse session = sessionService.create(new SessionRequest(currentMonthLabel(),
                currentMonthStart(), currentMonthEnd()));

        ContributionDefinitionResponse c50k = createActiveDefinition("Cotisation 50 000 FCFA",
                new BigDecimal(50000), AmountMode.FIXED, true, FundDestination.TONTINE_FUND, allMembers);
        ContributionDefinitionResponse c10k = createActiveDefinition("Cotisation 10 000 FCFA",
                new BigDecimal(10000), AmountMode.FIXED, true, FundDestination.TONTINE_FUND, allMembers);
        ContributionDefinitionResponse ration = createActiveDefinition("Ration",
                new BigDecimal(2000), AmountMode.FIXED, true, FundDestination.TONTINE_FUND, allMembers);
        ContributionDefinitionResponse epargne = createActiveDefinition("Banque / Épargne",
                null, AmountMode.VOLUNTARY, false, FundDestination.INDIVIDUAL_SAVINGS, allMembers);

        LocalDate dueDate = currentMonthStart().plusDays(14);
        ContributionPeriodResponse period50k = definitionService.addPeriod(c50k.id(), new ContributionPeriodRequest(session.id(), dueDate));
        ContributionPeriodResponse period10k = definitionService.addPeriod(c10k.id(), new ContributionPeriodRequest(session.id(), dueDate));
        ContributionPeriodResponse periodRation = definitionService.addPeriod(ration.id(), new ContributionPeriodRequest(session.id(), dueDate));
        definitionService.addPeriod(epargne.id(), new ContributionPeriodRequest(session.id(), null));

        declareAndValidate(jean, tresorier, c50k.id(), period50k.id(), new BigDecimal(50000),
                PaymentOperator.MTN_MOMO, "MOMO-DEMO-001", currentMonthStart().plusDays(3));
        declarePendingOnly(marie, c50k.id(), period50k.id(), new BigDecimal(50000),
                PaymentOperator.ORANGE_MONEY, "OM-DEMO-002", currentMonthStart().plusDays(5));
        declareAndValidate(jean, tresorier, c10k.id(), period10k.id(), new BigDecimal(10000),
                PaymentOperator.MTN_MOMO, "MOMO-DEMO-003", currentMonthStart().plusDays(3));
        declareAndValidate(paul, tresorier, ration.id(), periodRation.id(), new BigDecimal(2000),
                PaymentOperator.CASH, "CASH-DEMO-004", currentMonthStart().plusDays(6));

        loginAs(tresorier);
        // Exemple du CLAUDE.md §18, repris tel quel pour la démo (pas une décision définitive).
        sanctionRuleService.create(new SanctionRuleRequest(c50k.id(), SanctionType.MONETARY, 3, new BigDecimal(2000), null));

        loginAs(admin);
        loanPolicyService.create(new LoanPolicyRequest(
                "Politique de prêt standard (démo)",
                "Valeurs illustratives pour la démonstration - à valider par le bureau de l'AEKD avant tout usage réel.",
                new BigDecimal(10000), new BigDecimal(500000), 12, null, null, null, 1));

        loginAs(jean);
        loanService.request(new LoanRequest(new BigDecimal(300000), 12, "Achat de matériel pour l'association"));

        loginAs(marie);
        LoanResponse marieLoan = loanService.request(new LoanRequest(new BigDecimal(50000), 3, "Frais scolaires"));

        loginAs(tresorier);
        LoanResponse approvedMarieLoan = loanService.approve(marieLoan.id(),
                new LoanApprovalRequest(new BigDecimal(50000), 3));
        loanRepaymentService.record(approvedMarieLoan.id(), new LoanRepaymentRequest(
                new BigDecimal(20000), currentMonthStart().plusDays(10), PaymentOperator.CASH, "REMB-DEMO-001"));
    }

    private User createUser(String email, String rawPassword, Role role) {
        User user = new User(email, passwordEncoder.encode(rawPassword));
        user.addRole(role);
        return userRepository.save(user);
    }

    private Member createMemberAccount(String email, String fullName, LocalDate joinDate, Role role) {
        User user = createUser(email, DEFAULT_PASSWORD_MEMBRE, role);
        Member member = new Member(user, fullName, joinDate);
        return memberRepository.save(member);
    }

    private ContributionDefinitionResponse createActiveDefinition(String name, BigDecimal amount, AmountMode mode,
                                                                    boolean mandatory, FundDestination destination,
                                                                    List<Member> participants) {
        ContributionDefinitionResponse definition = definitionService.create(new ContributionDefinitionRequest(
                name, null, amount, mode, ContributionFrequency.MONTHLY, mandatory, Visibility.PUBLIC, destination));
        for (Member member : participants) {
            definitionService.addParticipant(definition.id(), new MemberRefRequest(member.getId()));
        }
        return definitionService.activate(definition.id());
    }

    private void declareAndValidate(Member declarer, User validator, java.util.UUID definitionId, java.util.UUID periodId,
                                     BigDecimal amount, PaymentOperator operator, String reference, LocalDate paymentDate) {
        ContributionTransactionResponse tx = declarePendingOnly(declarer, definitionId, periodId, amount, operator,
                reference, paymentDate);
        loginAs(validator);
        transactionService.validate(tx.id());
    }

    private ContributionTransactionResponse declarePendingOnly(Member declarer, java.util.UUID definitionId,
                                                                 java.util.UUID periodId, BigDecimal amount,
                                                                 PaymentOperator operator, String reference,
                                                                 LocalDate paymentDate) {
        loginAs(declarer);
        return paymentService.declare(definitionId, new ContributionTransactionRequest(
                periodId, amount, operator, reference, paymentDate, null));
    }

    private void loginAs(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities));
    }

    private void loginAs(Member member) {
        loginAs(member.getUser());
    }

    private static LocalDate currentMonthStart() {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(1);
    }

    private static LocalDate currentMonthEnd() {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(today.lengthOfMonth());
    }

    private static String currentMonthLabel() {
        LocalDate today = LocalDate.now();
        String month = today.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
        return "Séance de " + month + " " + today.getYear();
    }
}
