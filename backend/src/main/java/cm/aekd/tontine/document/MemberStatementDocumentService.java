package cm.aekd.tontine.document;

import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.document.AekdPdf.Cell;
import cm.aekd.tontine.fund.FundService;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.repayment.LoanRepayment;
import cm.aekd.tontine.repayment.LoanRepaymentRepository;
import cm.aekd.tontine.repayment.LoanRepaymentStatus;
import cm.aekd.tontine.sanction.AppliedSanction;
import cm.aekd.tontine.sanction.AppliedSanctionRepository;
import cm.aekd.tontine.sanction.AppliedSanctionStatus;
import cm.aekd.tontine.sanction.SanctionType;
import cm.aekd.tontine.savings.SavingsDtos.EntryResponse;
import cm.aekd.tontine.savings.SavingsDtos.EntrySource;
import cm.aekd.tontine.savings.SavingsDtos.MemberSavingsResponse;
import cm.aekd.tontine.savings.SavingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static cm.aekd.tontine.document.DocumentFormat.date;
import static cm.aekd.tontine.document.DocumentFormat.fcfa;

/**
 * Relevé individuel d'un membre : paiements de cotisations, épargne
 * individuelle, versements au fonds commun, prêts, remboursements et sanctions.
 * Les totaux ne comptent que les opérations validées (CLAUDE.md §14, §24) ;
 * épargne individuelle et fonds commun restent séparés (§17).
 */
@Service
@Transactional(readOnly = true)
public class MemberStatementDocumentService {

    private static final List<LoanStatus> OUTSTANDING = List.of(LoanStatus.APPROVED, LoanStatus.IN_PROGRESS);

    private final MemberRepository memberRepository;
    private final ContributionTransactionRepository transactionRepository;
    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final AppliedSanctionRepository sanctionRepository;
    private final DocumentAccess access;
    private final FundService fundService;
    private final SavingsService savingsService;

    public MemberStatementDocumentService(MemberRepository memberRepository,
                                          ContributionTransactionRepository transactionRepository,
                                          LoanRepository loanRepository,
                                          LoanRepaymentRepository repaymentRepository,
                                          AppliedSanctionRepository sanctionRepository,
                                          DocumentAccess access,
                                          FundService fundService,
                                          SavingsService savingsService) {
        this.memberRepository = memberRepository;
        this.transactionRepository = transactionRepository;
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
        this.sanctionRepository = sanctionRepository;
        this.access = access;
        this.fundService = fundService;
        this.savingsService = savingsService;
    }

    public GeneratedDocument myStatement() {
        return statement(access.currentMemberIdOrThrow());
    }

    public GeneratedDocument statement(UUID memberId) {
        access.checkStaffOrMember(memberId, "Membre introuvable");
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));

        List<ContributionTransaction> transactions = transactionRepository.findByMemberId(memberId).stream()
                .sorted(Comparator.comparing(ContributionTransaction::getPaymentDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ContributionTransaction::getCreatedAt, Comparator.reverseOrder()))
                .toList();
        List<Loan> loans = loanRepository.findByMemberId(memberId).stream()
                .sorted(Comparator.comparing(Loan::getCreatedAt, Comparator.reverseOrder()))
                .toList();
        Map<UUID, List<LoanRepayment>> repaymentsByLoan = loans.stream()
                .collect(Collectors.toMap(Loan::getId, loan -> repaymentRepository.findByLoanId(loan.getId())));
        List<AppliedSanction> sanctions = sanctionRepository.findByMemberId(memberId).stream()
                .sorted(Comparator.comparing(AppliedSanction::getCreatedAt, Comparator.reverseOrder()))
                .toList();

        BigDecimal savings = fundService.individualSavingsOf(memberId);
        BigDecimal tontineFund = transactionRepository.sumAmountByStatusAndFundDestinationAndMember(
                ContributionTransactionStatus.VALIDATED, FundDestination.TONTINE_FUND, memberId);
        BigDecimal outstanding = loans.stream()
                .filter(loan -> OUTSTANDING.contains(loan.getStatus()))
                .map(loan -> remaining(loan, repaymentsByLoan.get(loan.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long activeSanctions = sanctions.stream().filter(s -> s.getStatus() == AppliedSanctionStatus.APPLIED).count();

        AekdPdf pdf = AekdPdf.portrait("Relevé individuel – " + member.getFullName(), access.currentAuthorName());
        pdf.heading("Relevé individuel du membre", member.getFullName());
        pdf.keyValues(List.of(
                new String[]{"Membre", member.getFullName()},
                new String[]{"Membre depuis", date(member.getJoinDate())},
                new String[]{"E-mail", member.getUser() != null ? member.getUser().getEmail() : null},
                new String[]{"Téléphone", member.getPhone()}
        ));
        pdf.statCards(List.of(
                new String[]{"Épargne individuelle", fcfa(savings)},
                new String[]{"Versé au fonds commun", fcfa(tontineFund)},
                new String[]{"Encours de prêts", fcfa(outstanding)},
                new String[]{"Sanctions actives", String.valueOf(activeSanctions)}
        ));

        pdf.section("Paiements de cotisations");
        List<Cell[]> payRows = new ArrayList<>();
        for (ContributionTransaction tx : transactions) {
            var period = tx.getContributionPeriod();
            payRows.add(new Cell[]{
                    Cell.of(date(tx.getPaymentDate())),
                    Cell.of(period.getContributionDefinition().getName()),
                    Cell.of(period.getSession().getLabel()),
                    Cell.of(DocumentFormat.destination(period.getContributionDefinition().getFundDestination())),
                    Cell.of(DocumentFormat.operator(tx.getOperator())),
                    Cell.of(fcfa(tx.getAmount())),
                    Cell.colored(DocumentFormat.transactionStatus(tx.getStatus()), transactionColor(tx.getStatus()))
            });
        }
        pdf.table(new String[]{"Date", "Cotisation", "Séance", "Destination", "Opérateur", "Montant", "Statut"},
                new float[]{1.2f, 2.4f, 2.2f, 1.6f, 1.6f, 1.4f, 1.2f}, payRows, 5);

        // Versements libres d'épargne (docs/decisions.md n° 39), avec les intérêts
        // acquis à ce jour calculés exactement comme sur la page « Mon épargne ».
        MemberSavingsResponse memberSavings = savingsService.ofMember(memberId);
        List<EntryResponse> deposits = memberSavings.entries().stream()
                .filter(e -> e.source() == EntrySource.DEPOSIT)
                .toList();
        pdf.section("Versements d'épargne");
        List<Cell[]> depositRows = new ArrayList<>();
        for (EntryResponse e : deposits) {
            depositRows.add(new Cell[]{
                    Cell.of(date(e.paymentDate())),
                    Cell.of(DocumentFormat.operator(e.operator())),
                    Cell.of(e.reference()),
                    Cell.of(fcfa(e.amount())),
                    Cell.of(e.interest() == null ? "–" : fcfa(e.interest()) + " (" + e.completeMonths() + " mois)"),
                    Cell.colored(DocumentFormat.savingsStatus(e.status()), savingsColor(e.status()))
            });
        }
        pdf.table(new String[]{"Date", "Opérateur", "Référence", "Montant", "Intérêts acquis", "Statut"},
                new float[]{1.2f, 1.7f, 2.2f, 1.4f, 1.8f, 1.3f}, depositRows, 3, 4);
        pdf.note(memberSavings.rateConfigured()
                ? "Épargne : " + fcfa(memberSavings.principal()) + " versés (versements libres et cotisations "
                + "d'épargne validés) + " + fcfa(memberSavings.interest()) + " d'intérêts = "
                + fcfa(memberSavings.total()) + ". Taux actuel : "
                + memberSavings.currentMonthlyRate().stripTrailingZeros().toPlainString().replace('.', ',')
                + " % par mois, intérêts simples par mois complet depuis la validation."
                : "Épargne : " + fcfa(memberSavings.principal()) + " versés (versements libres et cotisations "
                + "d'épargne validés). Aucun taux d'intérêt n'est encore fixé : aucun intérêt n'est calculé.");

        pdf.section("Prêts");
        List<Cell[]> loanRows = new ArrayList<>();
        for (Loan loan : loans) {
            List<LoanRepayment> repayments = repaymentsByLoan.get(loan.getId());
            boolean hasBalance = loan.getApprovedAmount() != null;
            loanRows.add(new Cell[]{
                    Cell.of(date(loan.getCreatedAt())),
                    Cell.of(fcfa(loan.getRequestedAmount())),
                    Cell.of(hasBalance ? fcfa(loan.getApprovedAmount()) : "–"),
                    Cell.of(hasBalance ? fcfa(loan.getTotalDue()) : "–"),
                    Cell.colored(DocumentFormat.loanStatus(loan.getStatus()), loanColor(loan.getStatus())),
                    Cell.of(hasBalance ? fcfa(repaid(repayments)) : "–"),
                    Cell.of(hasBalance ? fcfa(remaining(loan, repayments)) : "–")
            });
        }
        // À rembourser = capital approuvé + intérêts (decisions.md n° 19).
        pdf.table(new String[]{"Demandé le", "Demandé", "Approuvé", "À rembourser", "Statut", "Remboursé", "Reste"},
                new float[]{1.3f, 1.4f, 1.4f, 1.5f, 1.5f, 1.4f, 1.4f}, loanRows, 1, 2, 3, 5, 6);

        pdf.section("Remboursements");
        List<Cell[]> repaymentRows = loans.stream()
                .flatMap(loan -> repaymentsByLoan.get(loan.getId()).stream())
                .sorted(Comparator.comparing(LoanRepayment::getPaymentDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> new Cell[]{
                        Cell.of(date(r.getPaymentDate())),
                        Cell.of("PRE-" + DocumentFormat.shortRef(r.getLoan().getId())),
                        Cell.of(DocumentFormat.operator(r.getOperator())),
                        Cell.of(r.getReference()),
                        Cell.of(fcfa(r.getAmount())),
                        Cell.colored(DocumentFormat.repaymentStatus(r.getStatus()), repaymentColor(r.getStatus()))
                })
                .toList();
        pdf.table(new String[]{"Date", "Prêt", "Opérateur", "Référence", "Montant", "Statut"},
                new float[]{1.3f, 1.4f, 1.8f, 2.2f, 1.5f, 1.2f}, repaymentRows, 4);

        pdf.section("Sanctions");
        List<Cell[]> sanctionRows = new ArrayList<>();
        for (AppliedSanction s : sanctions) {
            var period = s.getContributionPeriod();
            boolean monetary = s.getSanctionRule().getType() == SanctionType.MONETARY;
            sanctionRows.add(new Cell[]{
                    Cell.of(date(s.getCreatedAt())),
                    Cell.of(period.getContributionDefinition().getName() + " – " + period.getSession().getLabel()),
                    Cell.of(DocumentFormat.sanctionType(s.getSanctionRule().getType())),
                    Cell.of(monetary ? fcfa(s.getAmount()) : s.getDescription()),
                    Cell.colored(DocumentFormat.sanctionStatus(s.getStatus()),
                            s.getStatus() == AppliedSanctionStatus.APPLIED ? AekdPdf.RED : AekdPdf.MUTED)
            });
        }
        pdf.table(new String[]{"Date", "Cotisation – séance", "Type", "Montant / description", "Statut"},
                new float[]{1.2f, 3.4f, 1.2f, 2.4f, 1.2f}, sanctionRows);

        pdf.note("Épargne individuelle et versements au fonds commun : paiements validés uniquement. "
                + "Encours de prêts calculé sur le capital approuvé, hors intérêts (mode de calcul à valider).");

        return new GeneratedDocument("releve-" + DocumentFormat.slug(member.getFullName()) + ".pdf", pdf.finish());
    }

    /** Seuls les remboursements validés réduisent le solde (docs/decisions.md n° 38). */
    private static BigDecimal repaid(List<LoanRepayment> repayments) {
        return repayments.stream()
                .filter(r -> r.getStatus() == LoanRepaymentStatus.VALIDATED)
                .map(LoanRepayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Color repaymentColor(LoanRepaymentStatus status) {
        return switch (status) {
            case VALIDATED -> AekdPdf.GREEN;
            case PENDING -> AekdPdf.ORANGE;
            case REJECTED -> AekdPdf.RED;
        };
    }

    private static BigDecimal remaining(Loan loan, List<LoanRepayment> repayments) {
        BigDecimal due = loan.getTotalDue() != null ? loan.getTotalDue() : BigDecimal.ZERO;
        return due.subtract(repaid(repayments));
    }

    private static Color savingsColor(String status) {
        return switch (status) {
            case "VALIDATED" -> AekdPdf.GREEN;
            case "PENDING" -> AekdPdf.ORANGE;
            default -> AekdPdf.RED;
        };
    }

    private static Color transactionColor(ContributionTransactionStatus status) {
        return switch (status) {
            case VALIDATED -> AekdPdf.GREEN;
            case PENDING -> AekdPdf.ORANGE;
            case REJECTED -> AekdPdf.RED;
        };
    }

    private static Color loanColor(LoanStatus status) {
        return switch (status) {
            case REQUESTED -> AekdPdf.ORANGE;
            case APPROVED, IN_PROGRESS -> AekdPdf.BLUE;
            case REPAID -> AekdPdf.GREEN;
            case REJECTED -> AekdPdf.RED;
        };
    }
}
