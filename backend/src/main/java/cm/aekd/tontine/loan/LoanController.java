package cm.aekd.tontine.loan;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §6 : "configurer les règles de prêt" est réservé à l'ADMIN
 * (le TRESORIER ne l'a pas dans sa liste de permissions, contrairement
 * aux sanctions). "Traiter/valider/rejeter les prêts" est réservé au
 * TRESORIER.
 */
@RestController
public class LoanController {

    private final LoanPolicyService policyService;
    private final LoanService loanService;

    public LoanController(LoanPolicyService policyService, LoanService loanService) {
        this.policyService = policyService;
        this.loanService = loanService;
    }

    @PostMapping("/api/loan-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LoanPolicyResponse> createPolicy(@Valid @RequestBody LoanPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.create(request));
    }

    @GetMapping("/api/loan-policies")
    public List<LoanPolicyResponse> listPolicies() {
        return policyService.findAll();
    }

    @PutMapping("/api/loan-policies/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public LoanPolicyResponse deactivatePolicy(@PathVariable UUID id) {
        return policyService.deactivate(id);
    }

    @PostMapping("/api/loans")
    public ResponseEntity<LoanResponse> request(@Valid @RequestBody LoanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.request(request));
    }

    @GetMapping("/api/loans/mine")
    public List<LoanResponse> mine() {
        return loanService.findMine();
    }

    @GetMapping("/api/loans")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<LoanResponse> all(@RequestParam(required = false) LoanStatus status) {
        return status != null ? loanService.findByStatus(status) : loanService.findAll();
    }

    @GetMapping("/api/loans/{id}")
    public LoanResponse get(@PathVariable UUID id) {
        return loanService.findById(id);
    }

    @PutMapping("/api/loans/{id}/approve")
    @PreAuthorize("hasRole('TRESORIER')")
    public LoanResponse approve(@PathVariable UUID id, @Valid @RequestBody LoanApprovalRequest request) {
        return loanService.approve(id, request);
    }

    @PutMapping("/api/loans/{id}/reject")
    @PreAuthorize("hasRole('TRESORIER')")
    public LoanResponse reject(@PathVariable UUID id, @Valid @RequestBody LoanRejectionRequest request) {
        return loanService.reject(id, request);
    }
}
