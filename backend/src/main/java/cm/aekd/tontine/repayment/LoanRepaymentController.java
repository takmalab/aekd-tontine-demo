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
 * CLAUDE.md §6 : "enregistrer les remboursements" est réservé au
 * TRESORIER. La consultation est ouverte au membre propriétaire du prêt
 * et à ADMIN/TRESORIER (contrôlé dans LoanRepaymentService).
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

    @GetMapping("/repayments")
    public List<LoanRepaymentResponse> list(@PathVariable UUID loanId) {
        return repaymentService.findByLoan(loanId);
    }

    @GetMapping("/balance")
    public LoanBalanceResponse balance(@PathVariable UUID loanId) {
        return repaymentService.balance(loanId);
    }
}
