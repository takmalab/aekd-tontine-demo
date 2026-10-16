package cm.aekd.tontine.share;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Message WhatsApp d'une séance, à partager par le bureau (docs/decisions.md n° 59). */
@RestController
public class WhatsappShareController {

    private final WhatsappShareService service;

    public WhatsappShareController(WhatsappShareService service) {
        this.service = service;
    }

    public record ShareText(String text) {
    }

    @GetMapping("/api/sessions/{sessionId}/share/{kind}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ShareText session(@PathVariable UUID sessionId, @PathVariable WhatsappShareService.Kind kind) {
        return new ShareText(service.sessionMessage(sessionId, kind));
    }
}
