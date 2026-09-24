package cm.aekd.tontine.contribution;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §28. La validation/le rejet est réservé au TRESORIER (§6 :
 * l'ADMIN ne peut que consulter les paiements, pas les valider/rejeter).
 */
@RestController
@RequestMapping("/api/contributions")
public class ContributionPaymentController {

    private final PaymentService paymentService;
    private final ContributionTransactionService transactionService;

    public ContributionPaymentController(PaymentService paymentService,
                                          ContributionTransactionService transactionService) {
        this.paymentService = paymentService;
        this.transactionService = transactionService;
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ContributionTransactionResponse> declare(@PathVariable UUID id,
                                                                      @Valid @RequestBody ContributionTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.declare(id, request));
    }

    @GetMapping("/payments/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<ContributionTransactionResponse> findPending() {
        return transactionService.findPending();
    }

    @GetMapping("/payments/mine")
    public List<ContributionTransactionResponse> findMine() {
        return transactionService.findMine();
    }

    @PutMapping("/payments/{id}/validate")
    @PreAuthorize("hasRole('TRESORIER')")
    public ContributionTransactionResponse validate(@PathVariable UUID id) {
        return transactionService.validate(id);
    }

    @PutMapping("/payments/{id}/reject")
    @PreAuthorize("hasRole('TRESORIER')")
    public ContributionTransactionResponse reject(@PathVariable UUID id) {
        return transactionService.reject(id);
    }
}
