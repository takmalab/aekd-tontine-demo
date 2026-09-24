package cm.aekd.tontine.sanction;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §6 : "configurer les sanctions" est permis à ADMIN et
 * TRESORIER. L'application/annulation réelle est réservée au TRESORIER
 * uniquement, par analogie avec la validation des paiements (seul rôle
 * explicitement chargé des actions opérationnelles de séance).
 */
@RestController
@RequestMapping("/api/sanctions")
public class SanctionController {

    private final SanctionRuleService ruleService;
    private final AppliedSanctionService appliedService;

    public SanctionController(SanctionRuleService ruleService, AppliedSanctionService appliedService) {
        this.ruleService = ruleService;
        this.appliedService = appliedService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<SanctionRuleResponse> createRule(@Valid @RequestBody SanctionRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<SanctionRuleResponse> listRules(@RequestParam UUID contributionDefinitionId) {
        return ruleService.findByDefinition(contributionDefinitionId);
    }

    @GetMapping("/{ruleId}/candidates")
    @PreAuthorize("hasRole('TRESORIER')")
    public List<SanctionCandidateResponse> candidates(@PathVariable UUID ruleId) {
        return appliedService.findCandidates(ruleId);
    }

    @PostMapping("/{ruleId}/apply")
    @PreAuthorize("hasRole('TRESORIER')")
    public ResponseEntity<AppliedSanctionResponse> apply(@PathVariable UUID ruleId,
                                                           @Valid @RequestBody ApplySanctionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appliedService.apply(ruleId, request));
    }

    @PutMapping("/applied/{id}/cancel")
    @PreAuthorize("hasRole('TRESORIER')")
    public AppliedSanctionResponse cancel(@PathVariable UUID id) {
        return appliedService.cancel(id);
    }

    @GetMapping("/applied/mine")
    public List<AppliedSanctionResponse> mine() {
        return appliedService.findMine();
    }

    @GetMapping("/applied")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<AppliedSanctionResponse> all(@RequestParam UUID contributionDefinitionId) {
        return appliedService.findByDefinition(contributionDefinitionId);
    }
}
