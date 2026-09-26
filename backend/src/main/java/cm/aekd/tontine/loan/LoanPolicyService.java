package cm.aekd.tontine.loan;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LoanPolicyService {

    private final LoanPolicyRepository policyRepository;
    private final LoanRepository loanRepository;
    private final AuditLogService auditLogService;

    public LoanPolicyService(LoanPolicyRepository policyRepository, LoanRepository loanRepository,
                              AuditLogService auditLogService) {
        this.policyRepository = policyRepository;
        this.loanRepository = loanRepository;
        this.auditLogService = auditLogService;
    }

    public LoanPolicyResponse create(LoanPolicyRequest request) {
        validateRequest(request);

        LoanPolicy policy = new LoanPolicy(request.name(), request.description(), request.minAmount(),
                request.maxAmount(), request.maxDurationMonths(), request.interestRate(),
                request.minSavingsRequired(), request.minSeniorityMonths(), request.maxActiveLoans());
        policy = policyRepository.save(policy);

        auditLogService.record(AuditAction.LOAN_POLICY_CREATED, "LoanPolicy", policy.getId(),
                "Création de la politique de prêt « " + policy.getName() + " »");

        return LoanPolicyResponse.from(policy);
    }

    public List<LoanPolicyResponse> findAll() {
        return policyRepository.findAll().stream()
                .map(LoanPolicyResponse::from)
                .toList();
    }

    /**
     * Modification complète des critères (Modifier). Refusée si la politique a
     * déjà été utilisée par au moins un prêt : les évaluations passées ne
     * doivent jamais changer de sens rétroactivement (§20/§21). Le trésorier
     * doit alors désactiver la politique et en créer une nouvelle.
     */
    public LoanPolicyResponse update(UUID id, LoanPolicyRequest request) {
        LoanPolicy policy = getOrThrow(id);
        if (loanRepository.existsByLoanPolicyId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette politique a déjà été utilisée par au moins un prêt : elle ne peut plus être modifiée. "
                            + "Désactivez-la et créez-en une nouvelle.");
        }
        validateRequest(request);

        policy.update(request.name(), request.description(), request.minAmount(), request.maxAmount(),
                request.maxDurationMonths(), request.interestRate(), request.minSavingsRequired(),
                request.minSeniorityMonths(), request.maxActiveLoans());
        policy = policyRepository.save(policy);

        auditLogService.record(AuditAction.LOAN_POLICY_UPDATED, "LoanPolicy", policy.getId(),
                "Modification de la politique de prêt « " + policy.getName() + " »");

        return LoanPolicyResponse.from(policy);
    }

    public LoanPolicyResponse activate(UUID id) {
        LoanPolicy policy = getOrThrow(id);
        policy.setActive(true);
        policy = policyRepository.save(policy);

        auditLogService.record(AuditAction.LOAN_POLICY_ACTIVATED, "LoanPolicy", policy.getId(),
                "Activation de la politique de prêt « " + policy.getName() + " »");

        return LoanPolicyResponse.from(policy);
    }

    public LoanPolicyResponse deactivate(UUID id) {
        LoanPolicy policy = getOrThrow(id);
        policy.setActive(false);
        policy = policyRepository.save(policy);

        auditLogService.record(AuditAction.LOAN_POLICY_DEACTIVATED, "LoanPolicy", policy.getId(),
                "Désactivation de la politique de prêt « " + policy.getName() + " »");

        return LoanPolicyResponse.from(policy);
    }

    /** Refusée si la politique a déjà été utilisée par au moins un prêt (intégrité de l'historique). */
    public void delete(UUID id) {
        LoanPolicy policy = getOrThrow(id);
        if (loanRepository.existsByLoanPolicyId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette politique a déjà été utilisée par au moins un prêt : elle ne peut pas être supprimée. "
                            + "Désactivez-la à la place.");
        }

        auditLogService.record(AuditAction.LOAN_POLICY_DELETED, "LoanPolicy", policy.getId(),
                "Suppression de la politique de prêt « " + policy.getName() + " »");

        policyRepository.delete(policy);
    }

    private LoanPolicy getOrThrow(UUID id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Politique introuvable"));
    }

    private void validateRequest(LoanPolicyRequest request) {
        validatePositiveIfPresent(request.minAmount(), "montant minimum");
        validatePositiveIfPresent(request.maxAmount(), "montant maximum");
        validatePositiveIfPresent(request.minSavingsRequired(), "épargne minimale");
        if (request.minAmount() != null && request.maxAmount() != null
                && request.minAmount().compareTo(request.maxAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant minimum ne peut pas dépasser le montant maximum");
        }
    }

    private void validatePositiveIfPresent(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le " + label + " doit être positif");
        }
    }
}
