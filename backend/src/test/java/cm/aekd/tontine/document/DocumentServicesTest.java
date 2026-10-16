package cm.aekd.tontine.document;

import cm.aekd.tontine.contribution.AmountMode;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionDefinitionRepository;
import cm.aekd.tontine.contribution.ContributionFrequency;
import cm.aekd.tontine.contribution.ContributionParticipant;
import cm.aekd.tontine.contribution.ContributionParticipantRepository;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.contribution.PaymentOperator;
import cm.aekd.tontine.contribution.Visibility;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanPolicy;
import cm.aekd.tontine.loan.LoanPolicyRepository;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.repayment.LoanRepayment;
import cm.aekd.tontine.repayment.LoanRepaymentRepository;
import cm.aekd.tontine.savings.SavingsDeposit;
import cm.aekd.tontine.savings.SavingsDepositRepository;
import cm.aekd.tontine.savings.SavingsDepositStatus;
import cm.aekd.tontine.security.RoleName;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionRepository;
import cm.aekd.tontine.user.Role;
import cm.aekd.tontine.user.RoleRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DocumentServicesTest {

    @Autowired private ReceiptDocumentService receiptService;
    @Autowired private SessionReportDocumentService sessionReportService;
    @Autowired private MemberStatementDocumentService statementService;
    @Autowired private ListExportDocumentService listExportService;
    @Autowired private ListExcelExportService listExcelExportService;

    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private ContributionDefinitionRepository definitionRepository;
    @Autowired private ContributionParticipantRepository participantRepository;
    @Autowired private ContributionPeriodRepository periodRepository;
    @Autowired private ContributionTransactionRepository transactionRepository;
    @Autowired private LoanPolicyRepository policyRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private LoanRepaymentRepository repaymentRepository;
    @Autowired private SavingsDepositRepository depositRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Member alice;
    private Member bruno;
    private User treasurer;
    private Session session;
    private ContributionPeriod period;
    private ContributionTransaction validated;
    private ContributionTransaction pending;

    @BeforeEach
    void setUp() {
        treasurer = createUser("doc-tresorier", RoleName.TRESORIER);
        alice = memberRepository.save(new Member(createUser("doc-alice", RoleName.MEMBRE), "Alice Ndongo", LocalDate.of(2025, 1, 10)));
        bruno = memberRepository.save(new Member(createUser("doc-bruno", RoleName.MEMBRE), "Bruno Essomba", LocalDate.of(2025, 3, 2)));

        session = sessionRepository.save(new Session("Séance d'octobre 2026", LocalDate.of(2026, 10, 3), "Douala", alice));
        ContributionDefinition definition = definitionRepository.save(new ContributionDefinition(
                "Cotisation 50 000 FCFA", null, new BigDecimal(50000), AmountMode.FIXED,
                ContributionFrequency.MONTHLY, true, Visibility.PUBLIC, FundDestination.TONTINE_FUND));
        participantRepository.save(new ContributionParticipant(definition, alice));
        participantRepository.save(new ContributionParticipant(definition, bruno));
        period = periodRepository.save(new ContributionPeriod(definition, session, LocalDate.of(2026, 10, 15)));

        validated = new ContributionTransaction(alice, period, new BigDecimal(50000), PaymentOperator.MTN_MOMO,
                "MTN-REF-001", LocalDate.of(2026, 10, 2), null);
        validated.setStatus(ContributionTransactionStatus.VALIDATED);
        validated.setValidatedBy(treasurer);
        validated.setValidatedAt(Instant.now());
        validated = transactionRepository.save(validated);
        pending = transactionRepository.saveAndFlush(new ContributionTransaction(bruno, period, new BigDecimal(20000),
                PaymentOperator.CASH, "CASH-002", LocalDate.of(2026, 10, 2), null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User createUser(String prefix, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(prefix + "@test.aekd.cm", passwordEncoder.encode("x"));
        user.addRole(role);
        return userRepository.save(user);
    }

    private void loginAs(String email, RoleName role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static String text(GeneratedDocument document) throws IOException {
        assertThat(new String(document.content(), 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(document.content());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder all = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            all.append(extractor.getTextFromPage(page)).append('\n');
        }
        // L'extracteur restitue les espaces insécables des montants comme des espaces multiples.
        return all.toString().replace('\u00A0', ' ').replaceAll("[ \t]+", " ");
    }

    @Test
    void memberDownloadsReceiptOfOwnValidatedPayment() throws IOException {
        loginAs("doc-alice@test.aekd.cm", RoleName.MEMBRE);

        GeneratedDocument receipt = receiptService.paymentReceipt(validated.getId());

        String text = text(receipt);
        assertThat(receipt.filename()).startsWith("recu-paiement-pai-").endsWith(".pdf");
        assertThat(text).contains("AMICALE DES ENFANTS KAMKOP DE DOUALA", "Alice Ndongo", "50 000 FCFA",
                "cinquante mille francs CFA", "MTN Mobile Money", "MTN-REF-001");
    }

    @Test
    void receiptIsRefusedForPendingPaymentAndForAnotherMember() {
        loginAs("doc-tresorier@test.aekd.cm", RoleName.TRESORIER);
        assertThatThrownBy(() -> receiptService.paymentReceipt(pending.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("validé");

        loginAs("doc-bruno@test.aekd.cm", RoleName.MEMBRE);
        assertThatThrownBy(() -> receiptService.paymentReceipt(validated.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void sessionReportListsEachMemberStatus() throws IOException {
        loginAs("doc-tresorier@test.aekd.cm", RoleName.TRESORIER);

        String text = text(sessionReportService.report(session.getId()));

        assertThat(text).contains("RAPPORT DE SÉANCE", "Séance d'octobre 2026", "Cotisation 50 000 FCFA",
                "Alice Ndongo", "Payé", "Bruno Essomba", "En attente", "Récapitulatif", "100 000 FCFA");
    }

    @Test
    void repaymentReceiptShowsRemainingBalance() throws IOException {
        loginAs("doc-tresorier@test.aekd.cm", RoleName.TRESORIER);
        LoanPolicy policy = policyRepository.save(new LoanPolicy("Politique test", null, null, null,
                null, null, null, null, null));
        Loan loan = new Loan(alice, policy, new BigDecimal(300000), 6, "Projet");
        loan.setApprovedAmount(new BigDecimal(300000));
        loan.setStatus(LoanStatus.IN_PROGRESS);
        loan = loanRepository.save(loan);
        LoanRepayment repayment = repaymentRepository.saveAndFlush(new LoanRepayment(loan, new BigDecimal(50000),
                LocalDate.of(2026, 10, 3), PaymentOperator.CASH, "REM-1", treasurer));

        String text = text(receiptService.repaymentReceipt(repayment.getId()));

        assertThat(text).contains("REÇU DE REMBOURSEMENT", "Alice Ndongo", "50 000 FCFA", "300 000 FCFA",
                "250 000 FCFA", "cinquante mille francs CFA");
    }

    @Test
    void memberStatementSeparatesSavingsAndTontineFund() throws IOException {
        SavingsDeposit deposit = new SavingsDeposit(alice, new BigDecimal(25000), LocalDate.of(2026, 10, 1),
                PaymentOperator.ORANGE_MONEY, "EPARGNE-OM-1", null, treasurer);
        deposit.setStatus(SavingsDepositStatus.VALIDATED);
        deposit.setValidatedAt(Instant.now());
        depositRepository.saveAndFlush(deposit);
        loginAs("doc-alice@test.aekd.cm", RoleName.MEMBRE);

        GeneratedDocument statement = statementService.myStatement();

        String text = text(statement);
        assertThat(statement.filename()).isEqualTo("releve-alice-ndongo.pdf");
        assertThat(text).contains("RELEVÉ INDIVIDUEL DU MEMBRE", "Alice Ndongo", "Épargne individuelle",
                "Versé au fonds commun", "Cotisation 50 000 FCFA", "Validé",
                "Versements et prélèvements d'épargne", "EPARGNE-OM-1", "Orange Money", "25 000 FCFA");

        loginAs("doc-alice@test.aekd.cm", RoleName.MEMBRE);
        assertThatThrownBy(() -> statementService.statement(bruno.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void listExportRendersGivenRowsAndSwitchesToLandscapeForWideTables() throws IOException {
        loginAs("doc-tresorier@test.aekd.cm", RoleName.TRESORIER);

        GeneratedDocument narrow = listExportService.export(new ListExportRequest("Liste des membres",
                "Recherche : « Alice »", List.of("Nom", "Statut"), List.of(List.of("Alice Ndongo", "Actif")), null));
        String text = text(narrow);
        assertThat(narrow.filename()).isEqualTo("liste-des-membres.pdf");
        assertThat(text).contains("LISTE DES MEMBRES", "1 élément", "Recherche : « Alice »", "Alice Ndongo");

        List<String> wide = List.of("A", "B", "C", "D", "E", "F", "G");
        GeneratedDocument landscape = listExportService.export(new ListExportRequest("Journal", null, wide,
                List.of(wide), List.of(6)));
        PdfReader reader = new PdfReader(landscape.content());
        assertThat(reader.getPageSizeWithRotation(1).getWidth())
                .isGreaterThan(reader.getPageSizeWithRotation(1).getHeight());
    }

    @Test
    void excelExportTurnsAmountsIntoNumbers() throws IOException {
        loginAs("doc-tresorier@test.aekd.cm", RoleName.TRESORIER);

        GeneratedDocument xlsx = listExcelExportService.export(new ListExportRequest("Sanctions – Cotisation 50 000",
                "Filtre : En cours", List.of("Membre", "Montant", "Statut"),
                List.of(List.of("Alice Ndongo", "2\u202F000 FCFA", "Appliquée"),
                        List.of("Paul Essomba", "Apporter les boissons", "Annulée")), null));

        assertThat(xlsx.filename()).isEqualTo("sanctions-cotisation-50-000.xlsx");
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx.content()))) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("Sanctions");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("2 éléments", "Filtre : En cours");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Montant");
            assertThat(sheet.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(2000d);
            assertThat(sheet.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Apporter les boissons");
        }
    }
}
