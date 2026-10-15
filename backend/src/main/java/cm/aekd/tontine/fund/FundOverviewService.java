package cm.aekd.tontine.fund;

import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
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
import cm.aekd.tontine.sanction.SanctionPaymentRepository;
import cm.aekd.tontine.sanction.SanctionPaymentStatus;
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
    private final SanctionPaymentRepository sanctionPaymentRepository;
    private final AccountOperationRepository operationRepository;

    public FundOverviewService(FundService fundService, ContributionTransactionRepository transactionRepository,
                               SessionPayoutRepository payoutRepository, SavingsDepositRepository depositRepository,
                               LoanRepository loanRepository, LoanRepaymentRepository repaymentRepository,
                               SanctionPaymentRepository sanctionPaymentRepository,
                               AccountOperationRepository operationRepository) {
        this.operationRepository = operationRepository;
        this.sanctionPaymentRepository = sanctionPaymentRepository;
        this.fundService = fundService;
        this.transactionRepository = transactionRepository;
        this.payoutRepository = payoutRepository;
        this.depositRepository = depositRepository;
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
    }

    /** Compte qui reçoit l'argent d'une cotisation, selon sa destination. */
    static FundAccount accountOf(FundDestination destination) {
        return switch (destination) {
            case TONTINE_FUND -> FundAccount.TONTINE_FUND;
            case INDIVIDUAL_SAVINGS -> FundAccount.SAVINGS;
            case RATION -> FundAccount.RATION;
            case CASH_FUND -> FundAccount.CASH_FUND;
        };
    }

    public FundOverviewResponse overview() {
        List<LoanRepayment> repayments = repaymentRepository.findByStatusOrderByCreatedAtAsc(LoanRepaymentStatus.VALIDATED);
        return new FundOverviewResponse(accounts(repayments), movements(repayments));
    }

    private List<AccountSummary> accounts(List<LoanRepayment> repayments) {
        BigDecimal rationRemitted = fundService.rationRemitted();
        return List.of(
                new AccountSummary(FundAccount.TONTINE_FUND, fundService.tontineFundBalance(), List.of(
                        new Figure(FigureCode.COLLECTED, fundService.tontineFundCollected()),
                        new Figure(FigureCode.REMITTED, fundService.tontineFundRemitted()))),
                new AccountSummary(FundAccount.SAVINGS, fundService.availableForLending(), List.of(
                        new Figure(FigureCode.TOTAL_SAVINGS, fundService.totalIndividualSavings()),
                        new Figure(FigureCode.OUTSTANDING_LOAN_CAPITAL, fundService.outstandingLoanCapital()),
                        new Figure(FigureCode.LOAN_INTEREST_COLLECTED, interestCollected(repayments)))),
                new AccountSummary(FundAccount.SANCTIONS, fundService.sanctionsBalance(), withOperations(
                        FundAccount.SANCTIONS, new Figure(FigureCode.COLLECTED, fundService.sanctionsCollected()))),
                new AccountSummary(FundAccount.CASH_FUND, fundService.cashFundBalance(), withOperations(
                        FundAccount.CASH_FUND, new Figure(FigureCode.COLLECTED, fundService.cashFundCollected()))),
                new AccountSummary(FundAccount.RATION, fundService.rationBalance(), withOperations(FundAccount.RATION,
                        new Figure(FigureCode.COLLECTED, fundService.rationCollected()),
                        new Figure(FigureCode.REMITTED, rationRemitted))));
    }

    /** Chiffres d'un compte opérable, suivis de ses dépenses et virements quand il y en a (n° 50). */
    private List<Figure> withOperations(FundAccount account, Figure... base) {
        List<Figure> figures = new ArrayList<>(List.of(base));
        BigDecimal spent = operationRepository.sumOutByType(account, AccountOperationType.EXPENSE);
        BigDecimal transferredOut = operationRepository.sumOutByType(account, AccountOperationType.TRANSFER);
        BigDecimal transferredIn = operationRepository.sumIn(account);
        if (spent.signum() > 0) {
            figures.add(new Figure(FigureCode.SPENT, spent));
        }
        if (transferredOut.signum() > 0) {
            figures.add(new Figure(FigureCode.TRANSFERRED_OUT, transferredOut));
        }
        if (transferredIn.signum() > 0) {
            figures.add(new Figure(FigureCode.TRANSFERRED_IN, transferredIn));
        }
        return figures;
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
                movements.add(new Movement(p.getPayoutDate(), MovementType.PAYOUT, FundAccount.TONTINE_FUND,
                        Direction.OUT, label, p.getMember().getFullName(), tontinePart));
            }
            if (rationPart.signum() > 0) {
                movements.add(new Movement(p.getPayoutDate(), MovementType.PAYOUT, FundAccount.RATION,
                        Direction.OUT, label, p.getMember().getFullName(), rationPart));
            }
        }
        depositRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> d.getStatus() == SavingsDepositStatus.VALIDATED)
                .forEach(d -> movements.add(new Movement(d.getPaymentDate(), MovementType.SAVINGS_DEPOSIT,
                        FundAccount.SAVINGS, Direction.IN, "Versement d'épargne", d.getMember().getFullName(), d.getAmount())));
        for (Loan loan : loanRepository.findByStatusIn(DISBURSED)) {
            movements.add(new Movement(date(loan.getDecisionDate()), MovementType.LOAN_DISBURSED, FundAccount.SAVINGS,
                    Direction.OUT, "Prêt accordé – " + loan.getApprovedDurationMonths() + " mois",
                    loan.getMember().getFullName(), loan.getApprovedAmount()));
        }
        for (LoanRepayment r : repayments) {
            movements.add(new Movement(r.getPaymentDate(), MovementType.LOAN_REPAYMENT, FundAccount.SAVINGS, Direction.IN,
                    "Remboursement de prêt", r.getLoan().getMember().getFullName(), r.getAmount()));
        }
        sanctionPaymentRepository.findByStatusOrderByCreatedAtAsc(SanctionPaymentStatus.VALIDATED).forEach(s -> {
            ContributionPeriod period = s.getAppliedSanction().getContributionPeriod();
            movements.add(new Movement(s.getPaymentDate(), MovementType.SANCTION_PAYMENT, FundAccount.SANCTIONS,
                    Direction.IN, "Sanction – " + period.getContributionDefinition().getName() + " – "
                    + period.getSession().getLabel(), s.getMember().getFullName(), s.getAmount()));
        });
        for (AccountOperation o : operationRepository.findAllByOrderByCreatedAtDesc()) {
            if (o.getType() == AccountOperationType.EXPENSE) {
                movements.add(new Movement(o.getOperationDate(), MovementType.EXPENSE, o.getSourceAccount(),
                        Direction.OUT, o.getLabel(), null, o.getAmount()));
            } else {
                movements.add(new Movement(o.getOperationDate(), MovementType.TRANSFER, o.getSourceAccount(),
                        Direction.OUT, o.getLabel(), null, o.getAmount()));
                movements.add(new Movement(o.getOperationDate(), MovementType.TRANSFER, o.getTargetAccount(),
                        Direction.IN, o.getLabel(), null, o.getAmount()));
            }
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
