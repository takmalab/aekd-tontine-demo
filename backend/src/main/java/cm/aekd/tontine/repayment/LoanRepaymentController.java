package cm.aekd.tontine.repayment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §6 : le TRESORIER enregistre les remboursements reçus
 * (comptabilisés immédiatement) ; le membre déclare ceux de son propre prêt,
 * qui restent en attente de validation (docs/decisions.md n° 38). La
 * consultation est ouverte au membre propriétaire du prêt et à
 * ADMIN/TRESORIER (contrôlé dans LoanRepaymentService).
 */
@RestController
@RequestMapping("/api/loans/{loanId}")
public class LoanRepaymentController {

    private final LoanRepaymentService repaymentService;

    public LoanRepaymentController(LoanRepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @PostMapping("/repayments")
    @PreAuthorize("hasRole('TRESORIER')")
    public ResponseEntity<LoanRepaymentResponse> record(@PathVariable UUID loanId,
                                                          @Valid @RequestBody LoanRepaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repaymentService.record(loanId, request));
    }

    /** Le membre déclare un remboursement de son propre prêt (en attente de validation). */
    /** Prélèvement sur l'épargne de l'emprunteur ou de l'avaliste (decisions.md n° 53). */
    @PostMapping("/savings-deduction")
    @PreAuthorize("hasRole('TRESORIER')")
    public ResponseEntity<LoanRepaymentResponse> deductFromSavings(@PathVariable UUID loanId,
                                                                   @Valid @RequestBody SavingsDeductionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repaymentService.deductFromSavings(loanId, request));
    }

    @PostMapping("/repayments/declare")
    public ResponseEntity<LoanRepaymentResponse> declare(@PathVariable UUID loanId,
                                                           @Valid @RequestBody LoanRepaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repaymentService.declare(loanId, request));
    }

    @GetMapping("/repayments")
    public List<LoanRepaymentResponse> list(@PathVariable UUID loanId) {
        return repaymentService.findByLoan(loanId);
    }

    @GetMapping("/balance")
    public LoanBalanceResponse balance(@PathVariable UUID loanId) {
        return repaymentService.balance(loanId);
    }
}
