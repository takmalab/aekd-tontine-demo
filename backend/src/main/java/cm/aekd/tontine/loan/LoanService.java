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
 * d'épargne disponible pour prêter (docs/decisions.md n° 46), ou si l'avaliste
 * désigné n'a pas encore accepté (n° 52).
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
    private final GuarantorStandingService guarantorStandingService;

    public LoanService(LoanRepository loanRepository, LoanPolicyRepository policyRepository,
                        LoanRuleEvaluationRepository evaluationRepository, MemberRepository memberRepository,
                        FundService fundService, CurrentUserProvider currentUserProvider,
                        UserRepository userRepository, AuditLogService auditLogService,
                        NotificationService notificationService,
                        GuarantorStandingService guarantorStandingService) {
        this.guarantorStandingService = guarantorStandingService;
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

        Member guarantor = resolveGuarantor(request.guarantorMemberId(), member, policy);

        Loan loan = new Loan(member, policy, request.amount(), request.durationMonths(), request.reason());
        loan.designateGuarantor(guarantor);
        loan = loanRepository.save(loan);

        List<LoanRuleEvaluation> evaluations = evaluateRules(loan, member, policy, activeLoansBeforeThisRequest);
        if (guarantor != null) {
            evaluations.add(guarantorEvaluation(loan, guarantor));
        }
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
        if (guarantor != null) {
            askGuarantor(loan);
        }

        return toResponse(loan);
    }

    // ---------- Avaliste (docs/decisions.md n° 52) ----------

    /** Membres que le demandeur peut désigner comme avaliste : actifs, sauf lui-même. */
    @Transactional(readOnly = true)
    public List<GuarantorCandidate> guarantorCandidates() {
        UUID memberId = currentUserProvider.currentMemberId().orElse(null);
        return memberRepository.findAll().stream()
                .filter(Member::isActive)
                .filter(m -> !m.getId().equals(memberId))
                .sorted(java.util.Comparator.comparing(Member::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new GuarantorCandidate(m.getId(), m.getFullName()))
                .toList();
    }

    public record GuarantorCandidate(UUID memberId, String fullName) {
    }

    /** Prêts dont le membre connecté est l'avaliste. */
    @Transactional(readOnly = true)
    public List<LoanResponse> findGuaranteed() {
        UUID memberId = currentUserProvider.currentMemberId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun profil membre associé à ce compte"));
        return loanRepository.findByGuarantorIdOrderByCreatedAtDesc(memberId).stream().map(this::toResponse).toList();
    }

    /** L'avaliste accepte ou refuse de garantir le prêt. */
    public LoanResponse answerGuarantee(UUID id, boolean accepted) {
        Loan loan = getOrThrow(id);
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (loan.getGuarantor() == null || currentMemberId == null
                || !currentMemberId.equals(loan.getGuarantor().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'êtes pas l'avaliste de ce prêt");
        }
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce prêt n'est plus en attente de décision");
        }
        if (loan.getGuarantorStatus() != GuarantorStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous avez déjà répondu pour ce prêt");
        }
        loan.guarantorAnswers(accepted);

        String guarantorName = loan.getGuarantor().getFullName();
        auditLogService.record(accepted ? AuditAction.LOAN_GUARANTEE_ACCEPTED : AuditAction.LOAN_GUARANTEE_DECLINED,
                "Loan", loan.getId(), guarantorName + (accepted ? " accepte" : " refuse")
                        + " d'être l'avaliste du prêt de " + loan.getMember().getFullName());
        NotificationType type = accepted ? NotificationType.LOAN_GUARANTEE_ACCEPTED : NotificationType.LOAN_GUARANTEE_DECLINED;
        String title = accepted ? "Avaliste d'accord" : "Avaliste : refus";
        notificationService.notifyMember(loan.getMember(), type, title, accepted
                ? guarantorName + " accepte d'être l'avaliste de votre demande de prêt."
                : guarantorName + " refuse d'être l'avaliste de votre demande de prêt. Choisissez un autre avaliste.",
                "/my-loans");
        notificationService.notifyStaff(type, title, guarantorName + (accepted ? " accepte" : " refuse")
                + " d'être l'avaliste du prêt demandé par " + loan.getMember().getFullName() + ".", "/loans");
        return toResponse(loan);
    }

    /** Le demandeur désigne un autre avaliste, tant que le prêt n'est pas décidé ni garanti. */
    public LoanResponse changeGuarantor(UUID id, UUID guarantorMemberId) {
        Loan loan = getOrThrow(id);
        UUID currentMemberId = currentUserProvider.currentMemberId().orElse(null);
        if (currentMemberId == null || !currentMemberId.equals(loan.getMember().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le demandeur peut changer d'avaliste");
        }
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce prêt n'est plus en attente de décision");
        }
        if (loan.getGuarantorStatus() == GuarantorStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Votre avaliste a déjà accepté");
        }
        Member guarantor = resolveGuarantor(guarantorMemberId, loan.getMember(), loan.getLoanPolicy());
        if (guarantor == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un avaliste");
        }
        loan.designateGuarantor(guarantor);
        evaluationRepository.deleteByLoanIdAndRuleName(loan.getId(), GUARANTOR_RULE);
        evaluationRepository.save(guarantorEvaluation(loan, guarantor));

        auditLogService.record(AuditAction.LOAN_GUARANTOR_CHANGED, "Loan", loan.getId(),
                loan.getMember().getFullName() + " désigne " + guarantor.getFullName() + " comme avaliste");
        askGuarantor(loan);
        return toResponse(loan);
    }

    private static final String GUARANTOR_RULE = "AVALISTE_A_JOUR";

    /** Vérifie l'avaliste demandé ; null s'il n'y en a pas (et que la politique le permet). */
    private Member resolveGuarantor(UUID guarantorMemberId, Member borrower, LoanPolicy policy) {
        if (guarantorMemberId == null) {
            if (policy.isGuarantorRequired()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La politique de prêt exige un avaliste : choisissez un membre garant");
            }
            return null;
        }
        if (guarantorMemberId.equals(borrower.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas être votre propre avaliste");
        }
        Member guarantor = memberRepository.findById(guarantorMemberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avaliste introuvable"));
        if (!guarantor.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cet avaliste n'est plus un membre actif");
        }
        return guarantor;
    }

    /**
     * Règle « Avaliste à jour » : signalée au trésorier, ne bloque pas (décisions n° 52
     * et 53). Comprend la limite de prêts garantis fixée par la politique.
     */
    private LoanRuleEvaluation guarantorEvaluation(Loan loan, Member guarantor) {
        List<String> issues = new ArrayList<>(guarantorStandingService.issuesOf(guarantor.getId()));
        Integer limit = loan.getLoanPolicy().getMaxGuaranteesPerMember();
        if (limit != null) {
            long guaranteed = loanRepository.findByGuarantorIdOrderByCreatedAtDesc(guarantor.getId()).stream()
                    .filter(other -> !other.getId().equals(loan.getId()))
                    .filter(other -> ACTIVE_STATUSES.contains(other.getStatus()))
                    .filter(other -> other.getGuarantorStatus() != GuarantorStatus.DECLINED)
                    .count();
            if (guaranteed >= limit) {
                issues.add("déjà avaliste de " + guaranteed + " prêt(s) en cours (limite : " + limit + ")");
            }
        }
        return new LoanRuleEvaluation(loan, GUARANTOR_RULE, issues.isEmpty(), issues.isEmpty()
                ? "Avaliste à jour (" + guarantor.getFullName() + ")"
                : "Avaliste " + guarantor.getFullName() + " pas à jour : " + String.join(", ", issues));
    }

    private void askGuarantor(Loan loan) {
        notificationService.notifyMember(loan.getGuarantor(), NotificationType.LOAN_GUARANTEE_REQUESTED,
                "Demande d'aval", loan.getMember().getFullName() + " vous désigne comme avaliste de son prêt de "
                        + DocumentFormat.fcfa(loan.getRequestedAmount()) + " sur " + loan.getRequestedDurationMonths()
                        + " mois. Acceptez ou refusez.",
                "/my-loans");
    }

    public LoanResponse approve(UUID id, LoanApprovalRequest request) {
        Loan loan = getOrThrow(id);
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce prêt n'est plus en attente de décision");
        }
        checkNotOwnLoan(loan);
        User decider = currentDeciderOrThrow();

        // L'avaliste désigné doit avoir accepté (docs/decisions.md n° 52).
        if (loan.getGuarantor() != null && loan.getGuarantorStatus() != GuarantorStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, loan.getGuarantorStatus() == GuarantorStatus.DECLINED
                    ? "Approbation impossible : l'avaliste a refusé. Le membre doit en désigner un autre."
                    : "Approbation impossible : l'avaliste n'a pas encore accepté.");
        }
        if (loan.getGuarantor() == null && loan.getLoanPolicy().isGuarantorRequired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Approbation impossible : la politique de prêt exige un avaliste.");
        }

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
        loan.applyInterest(loan.getLoanPolicy().getInterestRate(), loan.getLoanPolicy().getInterestPeriodMonths());

        // L'avaliste est revérifié au moment de l'approbation (decisions.md n° 53).
        if (loan.getGuarantor() != null) {
            evaluationRepository.deleteByLoanIdAndRuleName(loan.getId(), GUARANTOR_RULE);
            evaluationRepository.save(guarantorEvaluation(loan, loan.getGuarantor()));
        }
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
        boolean isGuarantor = currentMemberId != null && loan.getGuarantor() != null
                && currentMemberId.equals(loan.getGuarantor().getId());
        if (!isGuarantor && (currentMemberId == null || !currentMemberId.equals(loan.getMember().getId()))) {
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
        int period = approved && loan.getInterestPeriodMonths() != null ? loan.getInterestPeriodMonths()
                : approved ? 1 : loan.getLoanPolicy().getInterestPeriodMonths();
        BigDecimal interest = approved ? loan.getInterestAmount()
                : LoanInterest.interest(loan.getRequestedAmount(), rate, loan.getRequestedDurationMonths(), period);
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
                period,
                interest,
                totalDue,
                !approved,
                loan.getReason(),
                loan.getStatus(),
                loan.getDecisionDate(),
                loan.getDecisionBy() != null ? loan.getDecisionBy().getEmail() : null,
                loan.getRejectionReason(),
                evaluations,
                loan.getCreatedAt(),
                loan.getGuarantor() != null ? loan.getGuarantor().getId() : null,
                loan.getGuarantor() != null ? loan.getGuarantor().getFullName() : null,
                loan.getGuarantorStatus(),
                loan.getGuarantorDecidedAt()
        );
    }
}
