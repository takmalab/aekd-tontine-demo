package cm.aekd.tontine.fund;

import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.fund.FundOverviewResponse.Account;
import cm.aekd.tontine.fund.FundOverviewResponse.AccountSummary;
import cm.aekd.tontine.fund.FundOverviewResponse.Direction;
import cm.aekd.tontine.fund.FundOverviewResponse.Figure;
import cm.aekd.tontine.fund.FundOverviewResponse.FigureCode;
import cm.aekd.tontine.fund.FundOverviewResponse.Movement;
import cm.aekd.tontine.fund.FundOverviewResponse.MovementType;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanInterest;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.payout.SessionPayout;
import cm.aekd.tontine.payout.SessionPayoutRepository;
import cm.aekd.tontine.repayment.LoanRepayment;
import cm.aekd.tontine.repayment.LoanRepaymentRepository;
import cm.aekd.tontine.repayment.LoanRepaymentStatus;
import cm.aekd.tontine.savings.SavingsDepositRepository;
import cm.aekd.tontine.savings.SavingsDepositStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vue d'ensemble des comptes pour l'écran « Fonds » (ADMIN, TRESORIER ;
 * decisions.md n° 50). Ne fait qu'assembler des opérations déjà validées :
 * aucun montant n'est créé ici, et les comptes ne sont jamais mélangés
 * (CLAUDE.md §17).
 */
@Service
@Transactional(readOnly = true)
public class FundOverviewService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final List<LoanStatus> DISBURSED = List.of(LoanStatus.APPROVED, LoanStatus.IN_PROGRESS,
            LoanStatus.REPAID);

    private final FundService fundService;
    private final ContributionTransactionRepository transactionRepository;
    private final SessionPayoutRepository payoutRepository;
    private final SavingsDepositRepository depositRepository;
    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository repaymentRepository;

    public FundOverviewService(FundService fundService, ContributionTransactionRepository transactionRepository,
                               SessionPayoutRepository payoutRepository, SavingsDepositRepository depositRepository,
                               LoanRepository loanRepository, LoanRepaymentRepository repaymentRepository) {
        this.fundService = fundService;
        this.transactionRepository = transactionRepository;
        this.payoutRepository = payoutRepository;
        this.depositRepository = depositRepository;
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
    }

    /** Compte qui reçoit l'argent d'une cotisation, selon sa destination. */
    static Account accountOf(FundDestination destination) {
        return switch (destination) {
            case TONTINE_FUND -> Account.TONTINE_FUND;
            case INDIVIDUAL_SAVINGS -> Account.SAVINGS;
            case RATION -> Account.RATION;
            case CASH_FUND -> Account.CASH_FUND;
        };
    }

    public FundOverviewResponse overview() {
        List<LoanRepayment> repayments = repaymentRepository.findByStatusOrderByCreatedAtAsc(LoanRepaymentStatus.VALIDATED);
        return new FundOverviewResponse(accounts(repayments), movements(repayments));
    }

    private List<AccountSummary> accounts(List<LoanRepayment> repayments) {
        BigDecimal rationRemitted = fundService.rationRemitted();
        return List.of(
                new AccountSummary(Account.TONTINE_FUND, fundService.tontineFundBalance(), List.of(
                        new Figure(FigureCode.COLLECTED, fundService.tontineFundCollected()),
                        new Figure(FigureCode.REMITTED, fundService.tontineFundRemitted()))),
                new AccountSummary(Account.SAVINGS, fundService.availableForLending(), List.of(
                        new Figure(FigureCode.TOTAL_SAVINGS, fundService.totalIndividualSavings()),
                        new Figure(FigureCode.OUTSTANDING_LOAN_CAPITAL, fundService.outstandingLoanCapital()),
                        new Figure(FigureCode.LOAN_INTEREST_COLLECTED, interestCollected(repayments)))),
                new AccountSummary(Account.SANCTIONS, BigDecimal.ZERO, List.of()),
                new AccountSummary(Account.CASH_FUND, fundService.cashFundBalance(), List.of(
                        new Figure(FigureCode.COLLECTED, fundService.cashFundCollected()))),
                new AccountSummary(Account.RATION, fundService.rationCollected().subtract(rationRemitted), List.of(
                        new Figure(FigureCode.COLLECTED, fundService.rationCollected()),
                        new Figure(FigureCode.REMITTED, rationRemitted))));
    }

    private List<Movement> movements(List<LoanRepayment> repayments) {
        List<Movement> movements = new ArrayList<>();

        for (ContributionTransaction t : transactionRepository.findByStatus(ContributionTransactionStatus.VALIDATED)) {
            ContributionPeriod period = t.getContributionPeriod();
            movements.add(new Movement(t.getPaymentDate(), MovementType.CONTRIBUTION,
                    accountOf(period.getContributionDefinition().getFundDestination()), Direction.IN,
                    period.getContributionDefinition().getName() + " – " + period.getSession().getLabel(),
                    t.getMember().getFullName(), t.getAmount()));
        }
        // Une remise sort du fonds commun et de la Ration au prorata de la séance (n° 50).
        Map<UUID, BigDecimal[]> collectedBySession = new HashMap<>();
        for (SessionPayout p : payoutRepository.findAll()) {
            String label = "Cagnotte remise – " + p.getSession().getLabel();
            BigDecimal rationPart = fundService.rationPartOf(p, collectedBySession);
            BigDecimal tontinePart = p.getAmount().subtract(rationPart);
            if (tontinePart.signum() > 0) {
                movements.add(new Movement(p.getPayoutDate(), MovementType.PAYOUT, Account.TONTINE_FUND,
                        Direction.OUT, label, p.getMember().getFullName(), tontinePart));
            }
            if (rationPart.signum() > 0) {
                movements.add(new Movement(p.getPayoutDate(), MovementType.PAYOUT, Account.RATION,
                        Direction.OUT, label, p.getMember().getFullName(), rationPart));
            }
        }
        depositRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> d.getStatus() == SavingsDepositStatus.VALIDATED)
                .forEach(d -> movements.add(new Movement(d.getPaymentDate(), MovementType.SAVINGS_DEPOSIT,
                        Account.SAVINGS, Direction.IN, "Versement d'épargne", d.getMember().getFullName(), d.getAmount())));
        for (Loan loan : loanRepository.findByStatusIn(DISBURSED)) {
            movements.add(new Movement(date(loan.getDecisionDate()), MovementType.LOAN_DISBURSED, Account.SAVINGS,
                    Direction.OUT, "Prêt accordé – " + loan.getApprovedDurationMonths() + " mois",
                    loan.getMember().getFullName(), loan.getApprovedAmount()));
        }
        for (LoanRepayment r : repayments) {
            movements.add(new Movement(r.getPaymentDate(), MovementType.LOAN_REPAYMENT, Account.SAVINGS, Direction.IN,
                    "Remboursement de prêt", r.getLoan().getMember().getFullName(), r.getAmount()));
        }
        movements.sort(Comparator.comparing(Movement::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return movements;
    }

    /** Intérêts contenus dans les remboursements validés, prêt par prêt (decisions.md n° 19). */
    private static BigDecimal interestCollected(List<LoanRepayment> repayments) {
        Map<UUID, List<LoanRepayment>> byLoan = repayments.stream()
                .collect(Collectors.groupingBy(r -> r.getLoan().getId()));
        BigDecimal total = BigDecimal.ZERO;
        for (List<LoanRepayment> loanRepayments : byLoan.values()) {
            Loan loan = loanRepayments.get(0).getLoan();
            if (loan.getTotalDue() == null) {
                continue;
            }
            BigDecimal repaid = loanRepayments.stream().map(LoanRepayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(repaid.subtract(LoanInterest.capitalShare(repaid, loan.getApprovedAmount(), loan.getTotalDue())));
        }
        return total;
    }

    private static LocalDate date(Instant instant) {
        return instant != null ? LocalDate.ofInstant(instant, ZONE) : null;
    }
}
