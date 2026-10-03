package cm.aekd.tontine.savings;

import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.savings.SavingsDtos.DepositRequest;
import cm.aekd.tontine.savings.SavingsDtos.EntryResponse;
import cm.aekd.tontine.savings.SavingsDtos.MemberSavingsResponse;
import cm.aekd.tontine.savings.SavingsDtos.OverviewResponse;
import cm.aekd.tontine.savings.SavingsDtos.RateRequest;
import cm.aekd.tontine.savings.SavingsDtos.RateResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Épargne (docs/decisions.md n° 39) :
 * - membre : son épargne (« Mon épargne »), versement libre, justificatif ;
 * - ADMIN / TRESORIER : états généraux, historique, épargne d'un membre, taux ;
 * - TRESORIER : validation / rejet des versements (jamais les siens).
 */
@RestController
@RequestMapping("/api/savings")
public class SavingsController {

    private final SavingsService savingsService;

    public SavingsController(SavingsService savingsService) {
        this.savingsService = savingsService;
    }

    @GetMapping("/me/overview")
    public MemberSavingsResponse mine() {
        return savingsService.mine();
    }

    @PostMapping("/deposits")
    public ResponseEntity<EntryResponse> declare(@Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savingsService.declare(request));
    }

    @PostMapping(value = "/deposits/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EntryResponse uploadProof(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return savingsService.uploadProof(id, file);
    }

    @GetMapping("/deposits/{id}/proof")
    public ResponseEntity<InputStreamResource> downloadProof(@PathVariable UUID id) {
        StoredResource resource = savingsService.downloadProof(id);
        String filename = resource.filename() == null ? "justificatif" : resource.filename();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(resource.content()));
    }

    @GetMapping("/deposits/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<EntryResponse> pending() {
        return savingsService.pendingDeposits();
    }

    @PutMapping("/deposits/{id}/validate")
    @PreAuthorize("hasRole('TRESORIER')")
    public EntryResponse validate(@PathVariable UUID id) {
        return savingsService.validate(id);
    }

    @PutMapping("/deposits/{id}/reject")
    @PreAuthorize("hasRole('TRESORIER')")
    public EntryResponse reject(@PathVariable UUID id) {
        return savingsService.reject(id);
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public OverviewResponse overview() {
        return savingsService.overview();
    }

    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<EntryResponse> entries() {
        return savingsService.allEntries();
    }

    @GetMapping("/members/{memberId}/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public MemberSavingsResponse ofMember(@PathVariable UUID memberId) {
        return savingsService.ofMember(memberId);
    }

    /** Historique des taux : consultable par tout utilisateur connecté (transparence pour les membres). */
    @GetMapping("/rates")
    public List<RateResponse> rates() {
        return savingsService.rateHistory();
    }

    @PostMapping("/rates")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<RateResponse> setRate(@Valid @RequestBody RateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(savingsService.setRate(request));
    }
}
