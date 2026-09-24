package cm.aekd.tontine.loan;

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

    public LoanPolicyService(LoanPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public LoanPolicyResponse create(LoanPolicyRequest request) {
        validatePositiveIfPresent(request.minAmount(), "montant minimum");
        validatePositiveIfPresent(request.maxAmount(), "montant maximum");
        validatePositiveIfPresent(request.minSavingsRequired(), "épargne minimale");
        if (request.minAmount() != null && request.maxAmount() != null
                && request.minAmount().compareTo(request.maxAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant minimum ne peut pas dépasser le montant maximum");
        }

        LoanPolicy policy = new LoanPolicy(request.name(), request.description(), request.minAmount(),
                request.maxAmount(), request.maxDurationMonths(), request.interestRate(),
                request.minSavingsRequired(), request.minSeniorityMonths(), request.maxActiveLoans());
        return LoanPolicyResponse.from(policyRepository.save(policy));
    }

    public List<LoanPolicyResponse> findAll() {
        return policyRepository.findAll().stream()
                .map(LoanPolicyResponse::from)
                .toList();
    }

    public LoanPolicyResponse deactivate(UUID id) {
        LoanPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Politique introuvable"));
        policy.setActive(false);
        return LoanPolicyResponse.from(policyRepository.save(policy));
    }

    private void validatePositiveIfPresent(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le " + label + " doit être positif");
        }
    }
}
