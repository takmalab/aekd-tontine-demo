package cm.aekd.tontine.mail;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Paramètres e-mail : super administrateur uniquement (docs/decisions.md n° 56).
 * La clé Brevo reste dans les variables du serveur : elle n'est jamais renvoyée.
 */
@RestController
@RequestMapping("/api/settings/email")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Transactional
public class EmailSettingsController {

    private final EmailSettingsRepository repository;
    private final EmailSender emailSender;
    private final EmailProperties properties;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public EmailSettingsController(EmailSettingsRepository repository, EmailSender emailSender,
                                   EmailProperties properties, AuditLogService auditLogService,
                                   CurrentUserProvider currentUserProvider, UserRepository userRepository) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    /**
     * @param configured clé Brevo et expéditeur présents dans les variables du serveur
     * @param enabled    envoi réel autorisé (sinon simulation forcée)
     * @param live       les e-mails partent réellement (configuré et autorisé)
     */
    public record SettingsResponse(boolean configured, boolean enabled, boolean live, String provider,
                                   String fromAddress, String fromName, String updatedByEmail, Instant updatedAt) {
    }

    public record SettingsRequest(@NotNull Boolean enabled) {
    }

    public record TestRequest(@NotBlank @Email String to) {
    }

    public record TestResponse(boolean live, String message) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return toResponse(settings());
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        EmailSettings settings = settings();
        settings.setEnabled(request.enabled());
        settings.setUpdatedBy(currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null));
        auditLogService.record(AuditAction.EMAIL_SETTINGS_UPDATED, "EmailSettings", EmailSettings.AUDIT_ENTITY_ID,
                request.enabled() ? "Envoi réel des e-mails autorisé" : "E-mails passés en simulation");
        return toResponse(settings);
    }

    @PostMapping("/test")
    public TestResponse sendTest(@Valid @RequestBody TestRequest request) {
        boolean live = emailSender.isLive();
        try {
            emailSender.send(request.to().trim(), "AEKD Tontine – e-mail de test",
                    "Ceci est un e-mail de test envoyé depuis l'écran « Paramètres e-mail » d'AEKD Tontine.",
                    "<div style=\"font-family:Arial,sans-serif;color:#3a2a1f\"><h2 style=\"color:#e86e09\">AEKD Tontine</h2>"
                            + "<p>Ceci est un e-mail de test envoyé depuis l'écran « Paramètres e-mail ».</p></div>");
        } catch (EmailSendException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "L'envoi a échoué : " + e.getMessage());
        }
        auditLogService.record(AuditAction.EMAIL_TEST_SENT, "EmailSettings", EmailSettings.AUDIT_ENTITY_ID,
                "E-mail de test " + (live ? "envoyé" : "simulé") + " vers " + request.to().trim());
        return new TestResponse(live, live
                ? "E-mail de test envoyé à " + request.to().trim() + ". Vérifiez la boîte de réception (et les spams)."
                : "Simulation : l'e-mail a été écrit dans les journaux du serveur, rien n'est parti.");
    }

    private EmailSettings settings() {
        return repository.findById(EmailSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Ligne email_settings absente (migration V31)"));
    }

    private SettingsResponse toResponse(EmailSettings settings) {
        return new SettingsResponse(emailSender.isConfigured(), settings.isEnabled(), emailSender.isLive(),
                properties.getProvider(), properties.getFromAddress(), properties.getFromName(),
                settings.getUpdatedBy() != null ? settings.getUpdatedBy().getEmail() : null, settings.getUpdatedAt());
    }
}
