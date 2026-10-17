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
 * CLAUDE.md §28. La validation/le rejet est réservé au TRESORIER (§6) ;
 * l'ADMIN, qui a tous les droits du TRESORIER, le peut aussi (hiérarchie de
 * rôles, docs/decisions.md n° 40).
 */
@RestController
@RequestMapping("/api/contributions")
public class ContributionPaymentController {

    private final PaymentService paymentService;
    private final ManualPaymentService manualPaymentService;
    private final ContributionTransactionService transactionService;

    public ContributionPaymentController(PaymentService paymentService, ManualPaymentService manualPaymentService,
                                          ContributionTransactionService transactionService) {
        this.paymentService = paymentService;
        this.manualPaymentService = manualPaymentService;
        this.transactionService = transactionService;
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ContributionTransactionResponse> declare(@PathVariable UUID id,
                                                                      @Valid @RequestBody ContributionTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.declare(id, request));
    }

    /** Décision n° 65 : paiement d'un membre enregistré par le trésorier, validé d'emblée. */
    @PostMapping("/{id}/payments/record")
    @PreAuthorize("hasRole('TRESORIER')")
    public ResponseEntity<ContributionTransactionResponse> record(@PathVariable UUID id,
                                                                     @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(manualPaymentService.record(id, request));
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
    public ContributionTransactionResponse reject(@PathVariable UUID id,
                                                  @Valid @RequestBody PaymentRejectionRequest request) {
        return transactionService.reject(id, request.reason());
    }
}
