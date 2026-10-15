package cm.aekd.tontine.fund;

import cm.aekd.tontine.fund.AccountOperationDtos.OperationRequest;
import cm.aekd.tontine.fund.AccountOperationDtos.OperationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dépenses et virements (decisions.md n° 50). Consultation ADMIN et TRESORIER ;
 * enregistrement par le trésorier (« gérer les fonds », CLAUDE.md §6), l'ADMIN
 * hérite de ce droit (n° 40).
 */
@RestController
@RequestMapping("/api/funds/operations")
public class AccountOperationController {

    private final AccountOperationService service;

    public AccountOperationController(AccountOperationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<OperationResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('TRESORIER')")
    public ResponseEntity<OperationResponse> record(@Valid @RequestBody OperationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(request));
    }
}
