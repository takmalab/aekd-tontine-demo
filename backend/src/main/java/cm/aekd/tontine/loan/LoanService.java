package cm.aekd.tontine.loan;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.document.DocumentFormat;
import cm.aekd.tontine.fund.FundService;
import cm.aekd.tontine.notification.NotificationService;
import cm.aekd.tontine.notification.NotificationType;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Demande et décision de prêt (CLAUDE.md §21-22). Les règles de la
 * politique active sont évaluées et expliquées (§21, regles-metier.md
 * §29) mais ne bloquent jamais la création de la demande ni la décision
 * du trésorier : docs/decisions.md ne tranche l'automatisation que pour
 * les sanctions (§10), pas pour les prêts, donc le trésorier garde toute
 * latitude pour approuver malgré une règle non respectée.
 *
 * Seule exception : l'approbation est bloquée si le montant dépasse l'argent
 * d'épargne disponible pour prêter (docs/decisions.md n° 46).
 */
@Service
@Transactional
public class LoanService {

    private static final List<LoanStatus> ACTIVE_STATUSES =
            List.of(LoanStatus.REQUESTED, LoanStatus.APPROVED, LoanStatus.IN_PROGRESS);

    private final LoanRepository loanRepository;
    private final LoanPolicyRepository policyRepository;
    private final LoanRuleEvaluationRepository evaluationRepository;
    private final MemberRepository memberRepository;
    private final FundService fundService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public LoanService(LoanRepository loanRepository, LoanPolicyRepository policyRepository,
                        LoanRuleEvaluationRepository evaluationRepository, MemberRepository memberRepository,
                        FundService fundService, CurrentUserProvider currentUserProvider,
                        UserRepository userRepository, AuditLogService auditLogService,
                        NotificationService notificationService) {
        this.notificationService = notificationService;
        this.loanRepository = loanRepository;
        this.policyRepository = policyRepository;
        this.evaluationRepository = evaluationRepository;
        this.memberRepository = memberRepository;
        this.fundService = fundService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    public LoanResponse request(LoanRequest request) {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));

        LoanPolicy policy = policyRepository.findFirstByActiveTrueOrderByCreatedAtDesc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Aucune politique de prêt active"));

        // Compté avant la création du prêt : sinon la demande en cours se compterait elle-même.
        long activeLoansBeforeThisRequest = policy.getMaxActiveLoans() != null
                ? loanRepository.countByMemberIdAndStatusIn(member.getId(), ACTIVE_STATUSES)
                : 0;

        Loan loan = new Loan(member, policy, request.amount(), request.durationMonths(), request.reason());
        loan = loanRepository.save(loan);

        List<LoanRuleEvaluation> evaluations = evaluateRules(loan, member, policy, activeLoansBeforeThisRequest);
        evaluationRepository.saveAll(evaluations);

        auditLogService.record(AuditAction.LOAN_REQUESTED, "Loan", loan.getId(),
                "Demande de prêt de " + loan.getRequestedAmount() + " par " + member.getFullName());
        long unmet = evaluations.stream().filter(e -> !e.isRespected()).count();
        notificationService.notifyStaff(NotificationType.LOAN_REQUESTED, "Nouvelle demande de prêt",
                member.getFullName() + " demande " + DocumentFormat.fcfa(loan.getRequestedAmount()) + " sur "
                        + loan.getRequestedDurationMonths() + " mois"
                        + (unmet > 0 ? " (" + unmet + " règle(s) non respectée(s))." : "."),
                "/loans");
        notificationService.notifyOtherMembersExceptStaff(member, null, "Demande de prêt",
                member.getFullName() + " demande un prêt de " + DocumentFormat.fcfa(loan.getRequestedAmount())
                        + " sur " + loan.getRequestedDurationMonths() + " mois.");

        return toResponse(loan);
    }

    public LoanResponse approve(UUID id, LoanApprovalRequest request) {
        Loan loan = getOrThrow(id);
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce prêt n'est plus en attente de décision");
        }
        checkNotOwnLoan(loan);
        User decider = currentDeciderOrThrow();

        // L'argent prêté vient de l'épargne des membres (docs/decisions.md n° 46) :
        // l'approbation est bloquée au-delà de l'argent d'épargne disponible.
        BigDecimal available = fundService.availableForLending();
        if (request.approvedAmount().compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Approbation impossible : le montant approuvé (" + DocumentFormat.fcfa(request.approvedAmount())
                            + ") dépasse l'argent d'épargne disponible pour prêter ("
                            + DocumentFormat.fcfa(available.max(BigDecimal.ZERO)) + ").");
        }

        loan.setApprovedAmount(request.approvedAmount());
        loan.setApprovedDurationMonths(request.approvedDurationMonths());
        // Taux de la politique figé sur le prêt : la modifier ensuite ne change pas ce prêt.
        loan.applyInterest(loan.getLoanPolicy().getInterestRate());
        loan.setStatus(LoanStatus.APPROVED);
        loan.setDecisionDate(Instant.now());
        loan.setDecisionBy(decider);
        loan = loanRepository.save(loan);

        auditLogService.record(AuditAction.LOAN_APPROVED, "Loan", loan.getId(),
                "Approbation du prêt pour " + request.approvedAmount() + " (intérêts " + loan.getInterestAmount()
                        + ", total à rembourser " + loan.getTotalDue() + ")");
        notificationService.notifyMember(loan.getMember(), NotificationType.LOAN_APPROVED, "Prêt approuvé",
                "Votre prêt est approuvé : " + DocumentFormat.fcfa(request.approvedAmount()) + " sur "
                        + request.approvedDurationMonths() + " mois. Total à rembourser : "
                        + DocumentFormat.fcfa(loan.getTotalDue()) + ".",
                "/my-loans");
        notificationService.notifyOtherMembers(loan.getMember(), null, "Prêt accordé",
                loan.getMember().getFullName() + " a obtenu un prêt de " + DocumentFormat.fcfa(request.approvedAmount())
                        + " sur " + request.approvedDurationMonths() + " mois.");

        return toResponse(loan);
    }

    public LoanResponse reject(UUID id, LoanRejectionRequest request) {
        Loan loan = getOrThrow(id);
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce prêt n'est plus en attente de décision");
        }
        checkNotOwnLoan(loan);
        User decider = currentDeciderOrThrow();

        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectionReason(request.rejectionReason());
        loan.setDecisionDate(Instant.now());
        loan.setDecisionBy(decider);
        loan = loanRepository.save(loan);

        auditLogService.record(AuditAction.LOAN_REJECTED, "Loan", loan.getId(),
                "Rejet du prêt : " + request.rejectionReason());
        notificationService.notifyMember(loan.getMember(), NotificationType.LOAN_REJECTED, "Prêt refusé",
                "Votre demande de prêt de " + DocumentFormat.fcfa(loan.getRequestedAmount()) + " a été refusée : "
                        + request.rejectionReason(),
                "/my-loans");
        // Le motif du refus reste réservé au demandeur.
        notificationService.notifyOtherMembers(loan.getMember(), null, "Prêt refusé",
                "La demande de prêt de " + DocumentFormat.fcfa(loan.getRequestedAmount()) + " de "
                        + loan.getMember().getFullName() + " a été refusée.");

        return toResponse(loan);
    }

    public LoanResponse findById(UUID id) {
        Loan loan = getOrThrow(id);
        checkCanView(loan);
        return toResponse(loan);
    }

    public List<LoanResponse> findMine() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return loanRepository.findByMemberId(memberId).stream().map(this::toResponse).toList();
    }

    public List<LoanResponse> findByStatus(LoanStatus status) {
        return loanRepository.findByStatus(status).stream().map(this::toResponse).toList();
    }

    public List<LoanResponse> findAll() {
        return loanRepository.findAll().stream().map(this::toResponse).toList();
    }

    private List<LoanRuleEvaluation> evaluateRules(Loan loan, Member member, LoanPolicy policy,
                                                    long activeLoansBeforeThisRequest) {
        List<LoanRuleEvaluation> evaluations = new ArrayList<>();

        if (policy.getMinAmount() != null) {
            boolean ok = loan.getRequestedAmount().compareTo(policy.getMinAmount()) >= 0;
            evaluations.add(new LoanRuleEvaluation(loan, "MONTANT_MINIMUM", ok, ok
                    ? "Montant demandé conforme au minimum (" + policy.getMinAmount() + ")"
                    : "Montant demandé (" + loan.getRequestedAmount() + ") inférieur au minimum autorisé ("
                            + policy.getMinAmount() + ")"));
        }
        if (policy.getMaxAmount() != null) {
            boolean ok = loan.getRequestedAmount().compareTo(policy.getMaxAmount()) <= 0;
            evaluations.add(new LoanRuleEvaluation(loan, "MONTANT_MAXIMUM", ok, ok
                    ? "Montant demandé conforme au maximum (" + policy.getMaxAmount() + ")"
                    : "Montant demandé (" + loan.getRequestedAmount() + ") supérieur au maximum autorisé ("
                            + policy.getMaxAmount() + ")"));
        }
        if (policy.getMaxDurationMonths() != null) {
            boolean ok = loan.getRequestedDurationMonths() <= policy.getMaxDurationMonths();
            evaluations.add(new LoanRuleEvaluation(loan, "DUREE_MAXIMALE", ok, ok
                    ? "Durée demandée conforme au maximum (" + policy.getMaxDurationMonths() + " mois)"
                    : "Durée demandée (" + loan.getRequestedDurationMonths() + " mois) supérieure au maximum autorisé ("
                            + policy.getMaxDurationMonths() + " mois)"));
        }
        if (policy.getMinSavingsRequired() != null) {
            BigDecimal savings = fundService.individualSavingsOf(member.getId());
            boolean ok = savings.compareTo(policy.getMinSavingsRequired()) >= 0;
            evaluations.add(new LoanRuleEvaluation(loan, "EPARGNE_MINIMALE", ok, ok
                    ? "Épargne individuelle suffisante (" + savings + ")"
                    : "Épargne individuelle (" + savings + ") inférieure au minimum requis ("
                            + policy.getMinSavingsRequired() + ")"));
        }
        if (policy.getMinSeniorityMonths() != null) {
            long seniorityMonths = ChronoUnit.MONTHS.between(member.getJoinDate(), LocalDate.now());
            boolean ok = seniorityMonths >= policy.getMinSeniorityMonths();
            evaluations.add(new LoanRuleEvaluation(loan, "ANCIENNETE_MINIMALE", ok, ok
                    ? "Ancienneté suffisante (" + seniorityMonths + " mois)"
                    : "Ancienneté (" + seniorityMonths + " mois) inférieure au minimum requis ("
                            + policy.getMinSeniorityMonths() + " mois)"));
        }
        if (policy.getMaxActiveLoans() != null) {
            boolean ok = activeLoansBeforeThisRequest < policy.getMaxActiveLoans();
            evaluations.add(new LoanRuleEvaluation(loan, "NOMBRE_PRETS_ACTIFS", ok, ok
                    ? "Nombre de prêts actifs conforme (" + activeLoansBeforeThisRequest + "/" + policy.getMaxActiveLoans() + ")"
                    : "Nombre de prêts actifs déjà atteint (" + activeLoansBeforeThisRequest + "/" + policy.getMaxActiveLoans() + ")"));
        }
        return evaluations;
    }

    /**
     * Personne ne décide pour lui-même (docs/decisions.md n° 23 et 40) : un
     * trésorier ou administrateur qui est aussi membre ne statue pas sur son
     * propre prêt.
     */
    private void checkNotOwnLoan(Loan loan) {
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId != null && currentMemberId.equals(loan.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas approuver ou rejeter votre propre demande de prêt");
        }
    }

    private User currentDeciderOrThrow() {
        return currentUserProvider.currentUserId()
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }

    private void checkCanView(Loan loan) {
        if (currentUserProvider.hasAnyRole("ADMIN", "TRESORIER")) {
            return;
        }
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(loan.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable");
        }
    }

    private Loan getOrThrow(UUID id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable"));
    }

    private LoanResponse toResponse(Loan loan) {
        List<LoanRuleEvaluationResponse> evaluations = evaluationRepository.findByLoanId(loan.getId()).stream()
                .map(LoanRuleEvaluationResponse::from)
                .toList();

        // Avant la décision, les intérêts sont estimés sur la demande avec le taux actuel
        // de la politique ; une fois le prêt approuvé, ce sont ceux figés sur le prêt.
        boolean approved = loan.getApprovedAmount() != null;
        BigDecimal rate = approved ? loan.getInterestRate() : loan.getLoanPolicy().getInterestRate();
        BigDecimal interest = approved ? loan.getInterestAmount()
                : LoanInterest.interest(loan.getRequestedAmount(), rate, loan.getRequestedDurationMonths());
        BigDecimal totalDue = approved ? loan.getTotalDue() : loan.getRequestedAmount().add(interest);

        return new LoanResponse(
                loan.getId(),
                loan.getMember().getId(),
                loan.getMember().getFullName(),
                loan.getLoanPolicy().getId(),
                loan.getLoanPolicy().getName(),
                loan.getRequestedAmount(),
                loan.getApprovedAmount(),
                loan.getRequestedDurationMonths(),
                loan.getApprovedDurationMonths(),
                rate,
                interest,
                totalDue,
                !approved,
                loan.getReason(),
                loan.getStatus(),
                loan.getDecisionDate(),
                loan.getDecisionBy() != null ? loan.getDecisionBy().getEmail() : null,
                loan.getRejectionReason(),
                evaluations,
                loan.getCreatedAt()
        );
    }
}
