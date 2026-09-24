package cm.aekd.tontine.loan;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.fund.FundService;
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

    public LoanService(LoanRepository loanRepository, LoanPolicyRepository policyRepository,
                        LoanRuleEvaluationRepository evaluationRepository, MemberRepository memberRepository,
                        FundService fundService, CurrentUserProvider currentUserProvider,
                        UserRepository userRepository, AuditLogService auditLogService) {
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

        return toResponse(loan);
    }

    public LoanResponse approve(UUID id, LoanApprovalRequest request) {
        Loan loan = getOrThrow(id);
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce prêt n'est plus en attente de décision");
        }
        User decider = currentDeciderOrThrow();

        loan.setApprovedAmount(request.approvedAmount());
        loan.setApprovedDurationMonths(request.approvedDurationMonths());
        loan.setStatus(LoanStatus.APPROVED);
        loan.setDecisionDate(Instant.now());
        loan.setDecisionBy(decider);
        loan = loanRepository.save(loan);

        auditLogService.record(AuditAction.LOAN_APPROVED, "Loan", loan.getId(),
                "Approbation du prêt pour " + request.approvedAmount());

        return toResponse(loan);
    }

    public LoanResponse reject(UUID id, LoanRejectionRequest request) {
        Loan loan = getOrThrow(id);
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce prêt n'est plus en attente de décision");
        }
        User decider = currentDeciderOrThrow();

        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectionReason(request.rejectionReason());
        loan.setDecisionDate(Instant.now());
        loan.setDecisionBy(decider);
        loan = loanRepository.save(loan);

        auditLogService.record(AuditAction.LOAN_REJECTED, "Loan", loan.getId(),
                "Rejet du prêt : " + request.rejectionReason());

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
