package cm.aekd.tontine.notification;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Notifications de l'utilisateur connecté, abonnements push et réglages des rappels. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> mine(@RequestParam(defaultValue = "50") int limit) {
        return notificationService.findMine(limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount());
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(id);
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/push/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", notificationService.publicKey());
    }

    @PostMapping("/push/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request,
                                          @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        notificationService.subscribe(request, userAgent);
        return ResponseEntity.noContent().build();
    }

    /** Désabonnement de ce navigateur (endpoint dans le corps : il contient des caractères spéciaux). */
    @PostMapping("/push/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody Map<String, String> body) {
        String endpoint = body.get("endpoint");
        if (endpoint != null && !endpoint.isBlank()) {
            notificationService.unsubscribe(endpoint);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push/test")
    public ResponseEntity<Void> test() {
        notificationService.sendTest();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public NotificationSettingsResponse settings() {
        return notificationService.settings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public NotificationSettingsResponse updateSettings(@Valid @RequestBody NotificationSettingsRequest request) {
        return notificationService.updateSettings(request);
    }
}
