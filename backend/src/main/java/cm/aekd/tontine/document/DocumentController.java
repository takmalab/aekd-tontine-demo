package cm.aekd.tontine.document;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Documents PDF aux couleurs de l'AEKD (docs/decisions.md n° 37).
 * Reçus et relevés : le membre concerné ou ADMIN/TRESORIER (contrôlé dans les
 * services). Rapport de séance et exports de listes : ADMIN/TRESORIER.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final ReceiptDocumentService receiptService;
    private final SessionReportDocumentService sessionReportService;
    private final MemberStatementDocumentService statementService;
    private final ListExportDocumentService listExportService;
    private final TournamentDocumentService tournamentDocumentService;

    public DocumentController(ReceiptDocumentService receiptService,
                              SessionReportDocumentService sessionReportService,
                              MemberStatementDocumentService statementService,
                              ListExportDocumentService listExportService,
                              TournamentDocumentService tournamentDocumentService) {
        this.tournamentDocumentService = tournamentDocumentService;
        this.receiptService = receiptService;
        this.sessionReportService = sessionReportService;
        this.statementService = statementService;
        this.listExportService = listExportService;
    }

    @GetMapping("/payments/{id}/receipt")
    public ResponseEntity<byte[]> paymentReceipt(@PathVariable UUID id) {
        return pdf(receiptService.paymentReceipt(id));
    }

    @GetMapping("/repayments/{id}/receipt")
    public ResponseEntity<byte[]> repaymentReceipt(@PathVariable UUID id) {
        return pdf(receiptService.repaymentReceipt(id));
    }

    @GetMapping("/sessions/{id}/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<byte[]> sessionReport(@PathVariable UUID id) {
        return pdf(sessionReportService.report(id));
    }

    @GetMapping("/members/me/statement")
    public ResponseEntity<byte[]> myStatement() {
        return pdf(statementService.myStatement());
    }

    @GetMapping("/members/{id}/statement")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<byte[]> memberStatement(@PathVariable UUID id) {
        return pdf(statementService.statement(id));
    }

    /** Ordre de passage d'un tournoi (decisions.md n° 59). */
    @GetMapping("/tournaments/{id}/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<byte[]> tournamentOrder(@PathVariable UUID id) {
        return pdf(tournamentDocumentService.order(id));
    }

    @PostMapping("/exports")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<byte[]> exportList(@Valid @RequestBody ListExportRequest request) {
        return pdf(listExportService.export(request));
    }

    private static ResponseEntity<byte[]> pdf(GeneratedDocument document) {
        // Noms de fichiers en ASCII (DocumentFormat.slug) : pas d'encodage RFC 2047 nécessaire.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(document.content());
    }
}
