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
 * CLAUDE.md §6 : création, participants, bénéficiaires, périodes et
 * activation sont réservés à ADMIN/TRESORIER. La consultation est ouverte
 * à tout utilisateur authentifié, filtrée par la visibilité (§11) dans
 * ContributionDefinitionService.
 */
@RestController
@RequestMapping("/api/contributions")
public class ContributionDefinitionController {

    private final ContributionDefinitionService service;

    public ContributionDefinitionController(ContributionDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<ContributionDefinitionResponse> create(@Valid @RequestBody ContributionDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public List<ContributionDefinitionResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ContributionDefinitionResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ContributionDefinitionResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/participants")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<ParticipantResponse> addParticipant(@PathVariable UUID id,
                                                                @Valid @RequestBody MemberRefRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addParticipant(id, request));
    }

    @GetMapping("/{id}/participants")
    public List<ParticipantResponse> listParticipants(@PathVariable UUID id) {
        return service.listParticipants(id);
    }

    @PostMapping("/{id}/beneficiaries")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<BeneficiaryResponse> addBeneficiary(@PathVariable UUID id,
                                                                @Valid @RequestBody MemberRefRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addBeneficiary(id, request));
    }

    @GetMapping("/{id}/beneficiaries")
    public List<BeneficiaryResponse> listBeneficiaries(@PathVariable UUID id) {
        return service.listBeneficiaries(id);
    }

    @PostMapping("/{id}/periods")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<ContributionPeriodResponse> addPeriod(@PathVariable UUID id,
                                                                   @Valid @RequestBody ContributionPeriodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addPeriod(id, request));
    }

    @GetMapping("/{id}/periods")
    public List<ContributionPeriodResponse> listPeriods(@PathVariable UUID id) {
        return service.listPeriods(id);
    }
}
