package cm.aekd.tontine.fund;

import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.fund.FundOverviewResponse.Direction;
import cm.aekd.tontine.fund.FundOverviewResponse.Fund;
import cm.aekd.tontine.fund.FundOverviewResponse.Movement;
import cm.aekd.tontine.fund.FundOverviewResponse.MovementType;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanInterest;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vue d'ensemble des fonds pour l'écran « Fonds » (ADMIN, TRESORIER). Ne fait
 * qu'assembler des opérations déjà validées : aucun montant n'est créé ici.
 * Le fonds commun (cotisations du fonds commun, remises aux bénéficiaires) et
 * l'épargne (cotisations d'épargne, versements libres, prêts, remboursements)
 * restent séparés (CLAUDE.md §17).
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

    public FundOverviewResponse overview() {
        List<LoanRepayment> repayments = repaymentRepository.findByStatusOrderByCreatedAtAsc(LoanRepaymentStatus.VALIDATED);
        List<Movement> movements = new ArrayList<>();

        for (ContributionTransaction t : transactionRepository.findByStatus(ContributionTransactionStatus.VALIDATED)) {
            ContributionPeriod period = t.getContributionPeriod();
            boolean savings = period.getContributionDefinition().getFundDestination() == FundDestination.INDIVIDUAL_SAVINGS;
            movements.add(new Movement(t.getPaymentDate(), MovementType.CONTRIBUTION,
                    savings ? Fund.SAVINGS : Fund.TONTINE_FUND, Direction.IN,
                    period.getContributionDefinition().getName() + " – " + period.getSession().getLabel(),
                    t.getMember().getFullName(), t.getAmount()));
        }
        payoutRepository.findAll().forEach(p -> movements.add(new Movement(p.getPayoutDate(), MovementType.PAYOUT,
                Fund.TONTINE_FUND, Direction.OUT, "Cagnotte remise – " + p.getSession().getLabel(),
                p.getMember().getFullName(), p.getAmount())));
        depositRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> d.getStatus() == SavingsDepositStatus.VALIDATED)
                .forEach(d -> movements.add(new Movement(d.getPaymentDate(), MovementType.SAVINGS_DEPOSIT,
                        Fund.SAVINGS, Direction.IN, "Versement d'épargne", d.getMember().getFullName(), d.getAmount())));
        for (Loan loan : loanRepository.findByStatusIn(DISBURSED)) {
            movements.add(new Movement(date(loan.getDecisionDate()), MovementType.LOAN_DISBURSED, Fund.SAVINGS,
                    Direction.OUT, "Prêt accordé – " + loan.getApprovedDurationMonths() + " mois",
                    loan.getMember().getFullName(), loan.getApprovedAmount()));
        }
        for (LoanRepayment r : repayments) {
            movements.add(new Movement(r.getPaymentDate(), MovementType.LOAN_REPAYMENT, Fund.SAVINGS, Direction.IN,
                    "Remboursement de prêt", r.getLoan().getMember().getFullName(), r.getAmount()));
        }
        movements.sort(Comparator.comparing(Movement::date, Comparator.nullsLast(Comparator.reverseOrder())));

        return new FundOverviewResponse(
                fundService.tontineFundCollected(),
                fundService.tontineFundRemitted(),
                fundService.tontineFundBalance(),
                fundService.totalIndividualSavings(),
                fundService.outstandingLoanCapital(),
                fundService.availableForLending(),
                interestCollected(repayments),
                movements);
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
