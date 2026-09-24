package cm.aekd.tontine.contribution;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * L'autorisation d'accès (propriétaire du paiement, ou ADMIN/TRESORIER)
 * est vérifiée dans PaymentProofService, pas ici : elle dépend de la
 * propriété de la ressource, pas seulement du rôle.
 */
@RestController
@RequestMapping("/api/contributions/payments")
public class PaymentProofController {

    private final PaymentProofService proofService;

    public PaymentProofController(PaymentProofService proofService) {
        this.proofService = proofService;
    }

    @PostMapping(value = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentProofResponse> upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proofService.upload(id, file));
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        StoredResource resource = proofService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitize(resource.filename()) + "\"")
                .body(new InputStreamResource(resource.content()));
    }

    private String sanitize(String filename) {
        return filename == null ? "justificatif" : filename.replaceAll("[\\r\\n\"]", "_");
    }
}
