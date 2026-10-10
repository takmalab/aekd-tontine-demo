package cm.aekd.tontine.payout;

import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutOverview;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutRequest;
import cm.aekd.tontine.payout.SessionPayoutDtos.PayoutResponse;
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

import java.util.UUID;

/** Cagnotte de la séance et remises aux bénéficiaires (ADMIN, TRESORIER ; decisions.md n° 46). */
@RestController
@RequestMapping("/api/sessions/{sessionId}/payouts")
@PreAuthorize("hasRole('TRESORIER')")
public class SessionPayoutController {

    private final SessionPayoutService payoutService;

    public SessionPayoutController(SessionPayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping
    public PayoutOverview overview(@PathVariable UUID sessionId) {
        return payoutService.overview(sessionId);
    }

    @PostMapping
    public ResponseEntity<PayoutResponse> record(@PathVariable UUID sessionId,
                                                 @Valid @RequestBody PayoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payoutService.record(sessionId, request));
    }
}
