package cm.aekd.tontine.fund;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.loan.LoanInterest;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.loan.LoanStatus;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.payout.SessionPayout;
import cm.aekd.tontine.payout.SessionPayoutRepository;
import cm.aekd.tontine.repayment.LoanRepaymentRepository;
import cm.aekd.tontine.sanction.SanctionPaymentRepository;
import cm.aekd.tontine.savings.SavingsDepositRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Solde du fonds commun et de l'épargne individuelle (CLAUDE.md §17,
 * §20-22), calculés à la volée à partir des ContributionTransaction
 * VALIDATED. Il n'existe pas d'entité "Fund" séparée dans le modèle de
 * données du §27 : un solde stocké risquerait de dériver des paiements
 * réels, alors qu'une agrégation reste toujours exacte à l'échelle du
 * MVP (moins de 30 membres, regles-metier.md §2). Seuls les paiements
 * VALIDATED sont comptabilisés ; fonds commun et épargne individuelle ne
 * sont jamais mélangés (§17 "IMPORTANT").
 *
 * L'épargne individuelle comprend aussi les versements libres VALIDÉS
 * (bouton « Épargner », docs/decisions.md n° 39). Montants en capital :
 * les intérêts d'épargne sont calculés à part (SavingsService).
 */
@Service
@Transactional(readOnly = true)
public class FundService {

    private final ContributionTransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final CurrentUserProvider currentUserProvider;
    private final SavingsDepositRepository depositRepository;
    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final SessionPayoutRepository payoutRepository;
    private final SanctionPaymentRepository sanctionPaymentRepository;
    private final AccountOperationRepository operationRepository;

    private static final List<LoanStatus> OUTSTANDING_LOAN_STATUSES =
            List.of(LoanStatus.APPROVED, LoanStatus.IN_PROGRESS);

    public FundService(ContributionTransactionRepository transactionRepository,
                        MemberRepository memberRepository,
                        CurrentUserProvider currentUserProvider,
                        SavingsDepositRepository depositRepository,
                        LoanRepository loanRepository,
                        LoanRepaymentRepository repaymentRepository,
                        SessionPayoutRepository payoutRepository,
                        SanctionPaymentRepository sanctionPaymentRepository,
                        AccountOperationRepository operationRepository) {
        this.operationRepository = operationRepository;
        this.sanctionPaymentRepository = sanctionPaymentRepository;
        this.payoutRepository = payoutRepository;
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.currentUserProvider = currentUserProvider;
        this.depositRepository = depositRepository;
        this.loanRepository = loanRepository;
        this.repaymentRepository = repaymentRepository;
    }

    /** Total des paiements validés destinés au fonds commun, depuis le début. */
    public BigDecimal tontineFundCollected() {
        return transactionRepository.sumAmountByStatusAndFundDestination(
                ContributionTransactionStatus.VALIDATED, FundDestination.TONTINE_FUND);
    }

    /**
     * Part du fonds commun déjà remise aux bénéficiaires (docs/decisions.md n° 46) :
     * total des remises moins leur part Ration (n° 50).
     */
    public BigDecimal tontineFundRemitted() {
        return payoutRepository.sumAll().subtract(rationRemitted());
    }

    /** Total des paiements validés destinés au compte Ration (decisions.md n° 50). */
    public BigDecimal rationCollected() {
        return transactionRepository.sumAmountByStatusAndFundDestination(
                ContributionTransactionStatus.VALIDATED, FundDestination.RATION);
    }

    /** Part Ration de toutes les remises de cagnotte (n° 50). */
    public BigDecimal rationRemitted() {
        Map<UUID, BigDecimal[]> collectedBySession = new HashMap<>();
        return payoutRepository.findAll().stream()
                .map(payout -> rationPartOf(payout, collectedBySession))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal rationBalance() {
        return withOperations(FundAccount.RATION, rationCollected().subtract(rationRemitted()));
    }

    /** Total des paiements validés destinés au Fond de caisse (decisions.md n° 50). */
    public BigDecimal cashFundCollected() {
        return transactionRepository.sumAmountByStatusAndFundDestination(
                ContributionTransactionStatus.VALIDATED, FundDestination.CASH_FUND);
    }

    /** Paiements de sanctions validés (decisions.md n° 50). */
    public BigDecimal sanctionsCollected() {
        return sanctionPaymentRepository.sumValidated();
    }

    public BigDecimal sanctionsBalance() {
        return withOperations(FundAccount.SANCTIONS, sanctionsCollected());
    }

    public BigDecimal cashFundBalance() {
        return withOperations(FundAccount.CASH_FUND, cashFundCollected());
    }

    /** Solde d'un compte opérable (Sanctions, Fond de caisse, Ration ; n° 50). */
    public BigDecimal balanceOf(FundAccount account) {
        return switch (account) {
            case SANCTIONS -> sanctionsBalance();
            case CASH_FUND -> cashFundBalance();
            case RATION -> rationBalance();
            case TONTINE_FUND -> tontineFundBalance();
            case SAVINGS -> availableForLending();
        };
    }

    /** Dépenses et virements émis (sortie), virements reçus (entrée) d'un compte (n° 50). */
    private BigDecimal withOperations(FundAccount account, BigDecimal balance) {
        return balance.subtract(operationRepository.sumOut(account)).add(operationRepository.sumIn(account));
    }

    /**
     * Part Ration d'une remise : la cagnotte d'une séance mêle fonds commun et Ration ;
     * chaque remise sort de chaque compte au prorata de ce que la séance a collecté
     * (decisions.md n° 50). Arrondie au franc inférieur, le reste revient au fonds commun.
     *
     * @param cache sommes collectées par séance (fonds commun, Ration), pour ne les lire qu'une fois
     */
    public BigDecimal rationPartOf(SessionPayout payout, Map<UUID, BigDecimal[]> cache) {
        UUID sessionId = payout.getSession().getId();
        BigDecimal[] collected = cache.computeIfAbsent(sessionId, id -> new BigDecimal[]{
                transactionRepository.sumValidatedMandatoryBySessionAndDestination(id, FundDestination.TONTINE_FUND),
                transactionRepository.sumValidatedMandatoryBySessionAndDestination(id, FundDestination.RATION)});
        BigDecimal total = collected[0].add(collected[1]);
        if (total.signum() == 0 || collected[1].signum() == 0) {
            return BigDecimal.ZERO;
        }
        return payout.getAmount().multiply(collected[1]).divide(total, 0, RoundingMode.DOWN);
    }

    /**
     * Fonds commun restant : collecté − remis aux bénéficiaires. L'argent du fonds
     * commun n'est pas conservé comme une caisse, il est remis au bénéficiaire de
     * la séance (docs/decisions.md n° 46).
     */
    public BigDecimal tontineFundBalance() {
        return tontineFundCollected().subtract(tontineFundRemitted());
    }

    public BigDecimal totalIndividualSavings() {
        return transactionRepository.sumAmountByStatusAndFundDestination(
                ContributionTransactionStatus.VALIDATED, FundDestination.INDIVIDUAL_SAVINGS)
                .add(depositRepository.sumValidated())
                .subtract(repaymentRepository.sumAllSavingsDeductions());
    }

    /**
     * Encours des prêts : capital approuvé des prêts APPROVED ou IN_PROGRESS,
     * moins la part de capital de leurs remboursements validés. Un remboursement
     * couvre capital et intérêts en proportion (decisions.md n° 19) : seule sa
     * part de capital redevient prêtable. Un prêt REPAID ne pèse plus rien.
     */
    public BigDecimal outstandingLoanCapital() {
        return loanRepository.findByStatusIn(OUTSTANDING_LOAN_STATUSES).stream()
                .map(loan -> loan.getApprovedAmount().subtract(LoanInterest.capitalShare(
                        repaymentRepository.sumAmountByLoanId(loan.getId()), loan.getApprovedAmount(),
                        loan.getTotalDue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Argent d'épargne disponible pour prêter (docs/decisions.md n° 46,
     * formule validée le 2026-10-05) : capital d'épargne validé − capital
     * des prêts approuvés ou en cours + capital déjà remboursé. Les intérêts
     * (d'épargne comme de prêts) n'en font pas partie.
     */
    public BigDecimal availableForLending() {
        return totalIndividualSavings().subtract(outstandingLoanCapital());
    }

    public BigDecimal individualSavingsOf(UUID memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable");
        }
        return savingsOf(memberId);
    }

    public BigDecimal myIndividualSavings() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return savingsOf(memberId);
    }

    private BigDecimal savingsOf(UUID memberId) {
        return transactionRepository.sumAmountByStatusAndFundDestinationAndMember(
                ContributionTransactionStatus.VALIDATED, FundDestination.INDIVIDUAL_SAVINGS, memberId)
                .add(depositRepository.sumValidatedByMemberId(memberId))
                // Prélèvements sur l'épargne pour rembourser un prêt (decisions.md n° 53).
                .subtract(repaymentRepository.sumSavingsDeductionsByPayer(memberId));
    }
}
