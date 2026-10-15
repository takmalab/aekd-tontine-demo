package cm.aekd.tontine.payout;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.contribution.ContributionDefinition;
import cm.aekd.tontine.contribution.ContributionPeriod;
import cm.aekd.tontine.contribution.ContributionPeriodRepository;
import cm.aekd.tontine.contribution.ContributionTransaction;
import cm.aekd.tontine.contribution.ContributionTransactionRepository;
import cm.aekd.tontine.contribution.ContributionTransactionStatus;
import cm.aekd.tontine.contribution.FundDestination;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.loan.GuarantorStatus;
import cm.aekd.tontine.loan.Loan;
import cm.aekd.tontine.loan.LoanRepository;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.repayment.LoanRepaymentService;
import cm.aekd.tontine.repayment.RepaymentSource;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.payout.SessionPayoutDtos.BeneficiaryShare;
import cm.aekd.tontine.payout.SessionPayoutDtos.ContributionLine;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutOverview;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutRequest;
import cm.aekd.tontine.payout.SessionPayoutDtos.DeductionLine;
import cm.aekd.tontine.payout.SessionPayoutDtos.DeductionOption;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutResponse;
import cm.aekd.tontine.session.Session;
import cm.aekd.tontine.session.SessionBeneficiary;
import cm.aekd.tontine.session.SessionBeneficiaryRepository;
import cm.aekd.tontine.session.SessionRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Remise de la cagnotte au(x) bénéficiaire(s) de la séance (docs/decisions.md
 * n° 46, précisions validées le 2026-10-05) :
 * <ul>
 *   <li>montant à remettre = paiements VALIDÉS des cotisations obligatoires du
 *       fonds commun rattachées à la séance (une cotisation d'épargne
 *       individuelle n'est jamais remise) ;</li>
 *   <li>plusieurs bénéficiaires : parts égales ;</li>
 *   <li>chaque remise est enregistrée par le trésorier ou l'administrateur, sans
 *       dépasser la part restante du bénéficiaire.</li>
 * </ul>
 * DÉCISION À VALIDER : une part est arrondie au franc inférieur ; le reliquat de
 * la division reste « non réparti » dans le fonds commun.
 */
@Service
@Transactional
public class SessionPayoutService {

    private final SessionRepository sessionRepository;
    private final SessionBeneficiaryRepository beneficiaryRepository;
    private final ContributionPeriodRepository periodRepository;
    private final ContributionTransactionRepository transactionRepository;
    private final SessionPayoutRepository payoutRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final LoanRepository loanRepository;
    private final LoanRepaymentService repaymentService;

    public SessionPayoutService(SessionRepository sessionRepository,
                                SessionBeneficiaryRepository beneficiaryRepository,
                                ContributionPeriodRepository periodRepository,
                                ContributionTransactionRepository transactionRepository,
                                SessionPayoutRepository payoutRepository,
                                CurrentUserProvider currentUserProvider,
                                UserRepository userRepository,
                                AuditLogService auditLogService,
                                NotificationService notificationService,
                                LoanRepository loanRepository,
                                LoanRepaymentService repaymentService) {
        this.loanRepository = loanRepository;
        this.repaymentService = repaymentService;
        this.sessionRepository = sessionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.periodRepository = periodRepository;
        this.transactionRepository = transactionRepository;
        this.payoutRepository = payoutRepository;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public PayoutOverview overview(UUID sessionId) {
        return buildOverview(getSessionOrThrow(sessionId));
    }

    public PayoutResponse record(UUID sessionId, PayoutRequest request) {
        Session session = getSessionOrThrow(sessionId);
        PayoutOverview overview = buildOverview(session);

        BeneficiaryShare share = overview.beneficiaries().stream()
                .filter(b -> b.memberId().equals(request.memberId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce membre n'est pas bénéficiaire de cette séance"));

        // Personne ne décide pour lui-même (docs/decisions.md n° 40).
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (request.memberId().equals(currentMemberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas enregistrer une remise à votre profit : un autre membre du bureau doit le faire");
        }

        if (request.amount().compareTo(share.due()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, share.due().signum() <= 0
                    ? share.memberFullName() + " a déjà reçu toute sa part de la cagnotte collectée."
                    : "La remise (" + DocumentFormat.fcfa(request.amount()) + ") dépasse la part restant à remettre à "
                            + share.memberFullName() + " (" + DocumentFormat.fcfa(share.due()) + ").");
        }

        User recordedBy = currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
        Member member = beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(SessionBeneficiary::getMember)
                .filter(m -> m.getId().equals(request.memberId()))
                .findFirst()
                .orElseThrow();

        // Retenues pour rembourser des prêts (decisions.md n° 53), vérifiées avant d'enregistrer.
        List<DeductionLine> deductions = request.deductions() == null ? List.of() : request.deductions();
        Map<UUID, DeductionOption> options = deductionOptions(member).stream()
                .collect(Collectors.toMap(DeductionOption::loanId, o -> o));
        BigDecimal deducted = BigDecimal.ZERO;
        for (DeductionLine line : deductions) {
            DeductionOption option = options.get(line.loanId());
            if (option == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce prêt ne concerne pas " + member.getFullName() + " (ni emprunteur ni avaliste)");
            }
            if (option.blockedReason() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, option.blockedReason());
            }
            if (line.amount().compareTo(option.outstanding()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La retenue pour le prêt de "
                        + option.borrowerFullName() + " dépasse ce qui reste à rembourser ("
                        + DocumentFormat.fcfa(option.outstanding()) + ")");
            }
            deducted = deducted.add(line.amount());
        }
        if (deducted.compareTo(request.amount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Les retenues dépassent le montant de la remise");
        }

        SessionPayout payout = new SessionPayout(session, member, request.amount(),
                request.payoutDate(), request.operator(), blankToNull(request.reference()),
                blankToNull(request.observation()), recordedBy);
        payout.setDeductedAmount(deducted);
        payout = payoutRepository.save(payout);
        for (DeductionLine line : deductions) {
            Loan loan = loanRepository.findById(line.loanId()).orElseThrow();
            repaymentService.applyDeduction(loan, line.amount(), request.payoutDate(),
                    RepaymentSource.PAYOUT_DEDUCTION, member, payout,
                    "Retenue sur la cagnotte – " + session.getLabel());
        }

        auditLogService.record(AuditAction.SESSION_PAYOUT_RECORDED, "SessionPayout", payout.getId(),
                "Remise de " + DocumentFormat.fcfa(request.amount()) + " à " + member.getFullName()
                        + " (cagnotte de la séance \"" + session.getLabel() + "\")");
        notificationService.notifyMember(member, NotificationType.SESSION_PAYOUT_RECEIVED, "Cagnotte remise",
                "Le trésorier a enregistré la remise de " + DocumentFormat.fcfa(request.amount())
                        + " pour la séance \"" + session.getLabel() + "\""
                        + (deducted.signum() > 0 ? ", dont " + DocumentFormat.fcfa(deducted)
                        + " retenus pour rembourser des prêts." : "."),
                "/sessions/" + session.getId());

        return PayoutResponse.from(payout);
    }

    /**
     * Prêts sur lesquels une retenue est possible à la remise de la cagnotte d'un
     * membre (decisions.md n° 53) : ses propres prêts en cours, puis ceux qu'il
     * garantit (seulement si l'épargne de l'emprunteur est épuisée).
     */
    @Transactional(readOnly = true)
    public List<DeductionOption> deductionOptions(UUID sessionId, UUID memberId) {
        getSessionOrThrow(sessionId);
        Member member = beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(SessionBeneficiary::getMember)
                .filter(m -> m.getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce membre n'est pas bénéficiaire de cette séance"));
        return deductionOptions(member);
    }

    private List<DeductionOption> deductionOptions(Member member) {
        LocalDate today = LocalDate.now(DocumentFormat.ZONE);
        List<DeductionOption> options = new ArrayList<>();
        for (Loan loan : loanRepository.findByMemberId(member.getId())) {
            BigDecimal outstanding = repaymentService.outstanding(loan);
            if (outstanding.signum() > 0) {
                options.add(new DeductionOption(loan.getId(), loan.getMember().getFullName(), "BORROWER",
                        outstanding, loan.isOverdue(today), null));
            }
        }
        for (Loan loan : loanRepository.findByGuarantorIdOrderByCreatedAtDesc(member.getId())) {
            BigDecimal outstanding = repaymentService.outstanding(loan);
            if (outstanding.signum() > 0 && loan.getGuarantorStatus() == GuarantorStatus.ACCEPTED) {
                options.add(new DeductionOption(loan.getId(), loan.getMember().getFullName(), "GUARANTOR",
                        outstanding, loan.isOverdue(today), repaymentService.guarantorBlockReason(loan)));
            }
        }
        return options;
    }

    private PayoutOverview buildOverview(Session session) {
        List<ContributionLine> lines = periodRepository.findBySessionId(session.getId()).stream()
                .filter(SessionPayoutService::isRemittedToBeneficiary)
                .map(period -> {
                    ContributionDefinition definition = period.getContributionDefinition();
                    BigDecimal collected = transactionRepository
                            .findByContributionPeriodIdAndStatus(period.getId(), ContributionTransactionStatus.VALIDATED)
                            .stream()
                            .map(ContributionTransaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ContributionLine(definition.getId(), definition.getName(), collected);
                })
                .toList();
        BigDecimal collected = lines.stream().map(ContributionLine::collected).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SessionPayout> payouts = payoutRepository.findBySessionIdOrderByPayoutDateAscCreatedAtAsc(session.getId());
        Map<UUID, BigDecimal> remittedByMember = payouts.stream().collect(Collectors.groupingBy(
                p -> p.getMember().getId(),
                Collectors.reducing(BigDecimal.ZERO, SessionPayout::getAmount, BigDecimal::add)));
        BigDecimal remitted = payouts.stream().map(SessionPayout::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SessionBeneficiary> beneficiaries = beneficiaryRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        BigDecimal share = beneficiaries.isEmpty()
                ? BigDecimal.ZERO
                : collected.divide(BigDecimal.valueOf(beneficiaries.size()), 0, RoundingMode.FLOOR);
        BigDecimal unallocated = beneficiaries.isEmpty()
                ? BigDecimal.ZERO
                : collected.subtract(share.multiply(BigDecimal.valueOf(beneficiaries.size())));

        List<BeneficiaryShare> shares = beneficiaries.stream()
                .map(b -> {
                    BigDecimal memberRemitted = remittedByMember.getOrDefault(b.getMember().getId(), BigDecimal.ZERO);
                    return new BeneficiaryShare(b.getMember().getId(), b.getMember().getFullName(), share,
                            memberRemitted, share.subtract(memberRemitted).max(BigDecimal.ZERO));
                })
                .toList();

        return new PayoutOverview(session.getId(), session.getLabel(), lines, collected, remitted,
                collected.subtract(remitted), unallocated, shares,
                payouts.stream().map(PayoutResponse::from).toList());
    }

    /**
     * Cotisation obligatoire du fonds commun ou de la Ration (n° 46 et 50) ; l'épargne
     * individuelle et le fond de caisse ne sont jamais remis.
     */
    private static boolean isRemittedToBeneficiary(ContributionPeriod period) {
        ContributionDefinition definition = period.getContributionDefinition();
        return definition.isMandatory() && (definition.getFundDestination() == FundDestination.TONTINE_FUND
                || definition.getFundDestination() == FundDestination.RATION);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Session getSessionOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
    }
}
