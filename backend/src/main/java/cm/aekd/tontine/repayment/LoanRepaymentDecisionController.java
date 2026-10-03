package cm.aekd.tontine.repayment;

import cm.aekd.tontine.contribution.StoredResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Remboursements déclarés par les membres (docs/decisions.md n° 38) : liste à
 * valider, validation / rejet par le TRESORIER, justificatif (dépôt par le
 * membre concerné, consultation par lui ou par ADMIN/TRESORIER).
 */
@RestController
@RequestMapping("/api/loans/repayments")
public class LoanRepaymentDecisionController {

    private final LoanRepaymentService repaymentService;

    public LoanRepaymentDecisionController(LoanRepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<LoanRepaymentResponse> pending() {
        return repaymentService.findPending();
    }

    @PutMapping("/{id}/validate")
    @PreAuthorize("hasRole('TRESORIER')")
    public LoanRepaymentResponse validate(@PathVariable UUID id) {
        return repaymentService.validate(id);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('TRESORIER')")
    public LoanRepaymentResponse reject(@PathVariable UUID id) {
        return repaymentService.reject(id);
    }

    @PostMapping(value = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LoanRepaymentResponse uploadProof(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return repaymentService.uploadProof(id, file);
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<InputStreamResource> downloadProof(@PathVariable UUID id) {
        StoredResource resource = repaymentService.downloadProof(id);
        String filename = resource.filename() == null ? "justificatif" : resource.filename();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(resource.content()));
    }
}
