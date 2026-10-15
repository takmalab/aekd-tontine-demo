package cm.aekd.tontine.sanction;

import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.sanction.SanctionPaymentDtos.DeclareRequest;
import cm.aekd.tontine.sanction.SanctionPaymentDtos.PaymentResponse;
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

/** Paiement des sanctions (decisions.md n° 50) : déclaré par le membre, validé par le trésorier. */
@RestController
@RequestMapping("/api/sanctions")
public class SanctionPaymentController {

    private final SanctionPaymentService service;

    public SanctionPaymentController(SanctionPaymentService service) {
        this.service = service;
    }

    @PostMapping("/applied/{sanctionId}/payments")
    public ResponseEntity<PaymentResponse> declare(@PathVariable UUID sanctionId,
                                                   @Valid @RequestBody DeclareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.declare(sanctionId, request));
    }

    @GetMapping("/payments/mine")
    public List<PaymentResponse> mine() {
        return service.mine();
    }

    @GetMapping("/payments/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public List<PaymentResponse> pending() {
        return service.pending();
    }

    @PutMapping("/payments/{id}/validate")
    @PreAuthorize("hasRole('TRESORIER')")
    public PaymentResponse validate(@PathVariable UUID id) {
        return service.validate(id);
    }

    @PutMapping("/payments/{id}/reject")
    @PreAuthorize("hasRole('TRESORIER')")
    public PaymentResponse reject(@PathVariable UUID id) {
        return service.reject(id);
    }

    @PostMapping(value = "/payments/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PaymentResponse uploadProof(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.uploadProof(id, file);
    }

    @GetMapping("/payments/{id}/proof")
    public ResponseEntity<InputStreamResource> downloadProof(@PathVariable UUID id) {
        StoredResource resource = service.downloadProof(id);
        String filename = resource.filename() == null ? "justificatif" : resource.filename();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(resource.content()));
    }
}
