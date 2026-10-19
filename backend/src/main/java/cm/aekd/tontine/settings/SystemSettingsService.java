package cm.aekd.tontine.settings;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.auth.otp.OtpProperties;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.document.AekdPdf;
import cm.aekd.tontine.security.JwtProperties;
import cm.aekd.tontine.storage.FileStorageService;
import cm.aekd.tontine.storage.StoredFile;
import cm.aekd.tontine.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Paramètres système (docs/decisions.md n° 57) : association (PDF), durée de
 * session, règles des codes SMS / e-mail, mode maintenance. Lus souvent, ils
 * sont gardés en mémoire et relus après chaque modification.
 */
@Service
@Transactional
public class SystemSettingsService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingsService.class);
    private static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg");
    private static final long LOGO_MAX_BYTES = 1024 * 1024;

    private final SystemSettingsRepository repository;
    private final OtpProperties otpProperties;
    private final JwtProperties jwtProperties;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    /** Copie en mémoire des réglages ; null = à relire. */
    private volatile Snapshot snapshot;

    public SystemSettingsService(SystemSettingsRepository repository, OtpProperties otpProperties,
                                 JwtProperties jwtProperties, FileStorageService fileStorageService,
                                 AuditLogService auditLogService, CurrentUserProvider currentUserProvider,
                                 UserRepository userRepository) {
        this.repository = repository;
        this.otpProperties = otpProperties;
        this.jwtProperties = jwtProperties;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    /**
     * Valeurs en vigueur (réglage, sinon configuration du serveur). Les champs du thème
     * (decisions.md n° 72) sont bruts : null = valeur par défaut, résolue par ThemeService.
     * {@code version} change à chaque modification (date de mise à jour en millisecondes).
     */
    public record Snapshot(String associationName, String associationShortName, String associationAddress,
                           String associationPhone, String associationEmail, boolean customLogo,
                           Integer sessionDays, Integer otpTtlMinutes, Integer otpMaxAttempts,
                           Integer otpResendSeconds, Integer otpMaxPerHour,
                           boolean maintenanceEnabled, String maintenanceMessage,
                           String themePrimaryColor, String themeSecondaryColor, String themeDarkColor,
                           String themeHeadingFont, String themeBodyFont, String themePattern,
                           String loginSlogan, boolean customLoginImage, long version) {
    }

    // ---------------------------------------------------------------- valeurs en vigueur

    public Snapshot current() {
        Snapshot current = snapshot;
        if (current == null) {
            current = reload();
        }
        return current;
    }

    public long sessionDays() {
        Integer days = current().sessionDays();
        return days != null ? days : jwtProperties.getRefreshTokenDays();
    }

    public Duration otpTtl() {
        Integer minutes = current().otpTtlMinutes();
        return minutes != null ? Duration.ofMinutes(minutes) : otpProperties.getTtl();
    }

    public int otpMaxAttempts() {
        Integer attempts = current().otpMaxAttempts();
        return attempts != null ? attempts : otpProperties.getMaxAttempts();
    }

    public Duration otpResendCooldown() {
        Integer seconds = current().otpResendSeconds();
        return seconds != null ? Duration.ofSeconds(seconds) : otpProperties.getResendCooldown();
    }

    public int otpMaxRequestsPerHour() {
        Integer max = current().otpMaxPerHour();
        return max != null ? max : otpProperties.getMaxRequestsPerHour();
    }

    public boolean maintenanceEnabled() {
        return current().maintenanceEnabled();
    }

    public String maintenanceMessage() {
        String message = current().maintenanceMessage();
        return message == null || message.isBlank()
                ? "L'application est en maintenance. Merci de revenir un peu plus tard."
                : message;
    }

    // ---------------------------------------------------------------- écran du super administrateur

    public record SettingsResponse(Snapshot settings, long effectiveSessionDays, long effectiveOtpTtlMinutes,
                                   int effectiveOtpMaxAttempts, long effectiveOtpResendSeconds,
                                   int effectiveOtpMaxPerHour, String updatedByEmail, java.time.Instant updatedAt) {
    }

    public record SettingsRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String associationName,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 30) String associationShortName,
            @jakarta.validation.constraints.Size(max = 255) String associationAddress,
            @jakarta.validation.constraints.Size(max = 30) String associationPhone,
            @jakarta.validation.constraints.Size(max = 255) String associationEmail,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(365) Integer sessionDays,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(60) Integer otpTtlMinutes,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20) Integer otpMaxAttempts,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(3600) Integer otpResendSeconds,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer otpMaxPerHour,
            boolean maintenanceEnabled,
            @jakarta.validation.constraints.Size(max = 500) String maintenanceMessage
    ) {
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        SystemSettings settings = entity();
        return new SettingsResponse(current(), sessionDays(), otpTtl().toMinutes(), otpMaxAttempts(),
                otpResendCooldown().toSeconds(), otpMaxRequestsPerHour(),
                settings.getUpdatedBy() != null ? settings.getUpdatedBy().getEmail() : null, settings.getUpdatedAt());
    }

    public SettingsResponse update(SettingsRequest request) {
        SystemSettings settings = entity();
        List<String> changes = new ArrayList<>();
        if (settings.isMaintenanceEnabled() != request.maintenanceEnabled()) {
            changes.add(request.maintenanceEnabled() ? "maintenance activée" : "maintenance désactivée");
        }
        settings.setAssociationName(request.associationName().trim());
        settings.setAssociationShortName(request.associationShortName().trim());
        settings.setAssociationAddress(blankToNull(request.associationAddress()));
        settings.setAssociationPhone(blankToNull(request.associationPhone()));
        settings.setAssociationEmail(blankToNull(request.associationEmail()));
        settings.setSessionDays(request.sessionDays());
        settings.setOtpTtlMinutes(request.otpTtlMinutes());
        settings.setOtpMaxAttempts(request.otpMaxAttempts());
        settings.setOtpResendSeconds(request.otpResendSeconds());
        settings.setOtpMaxPerHour(request.otpMaxPerHour());
        settings.setMaintenanceEnabled(request.maintenanceEnabled());
        settings.setMaintenanceMessage(blankToNull(request.maintenanceMessage()));
        settings.setUpdatedBy(currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null));
        repository.flush();
        auditLogService.record(AuditAction.SYSTEM_SETTINGS_UPDATED, "SystemSettings", SystemSettings.AUDIT_ENTITY_ID,
                "Paramètres système modifiés" + (changes.isEmpty() ? "" : " (" + String.join(", ", changes) + ")"));
        reload();
        return get();
    }

    /** Logo de l'association (interface, icônes de l'application, PDF) : PNG ou JPEG, 1 Mo au plus. */
    public SettingsResponse uploadLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune image fournie");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!LOGO_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Format accepté : PNG ou JPEG");
        }
        if (file.getSize() > LOGO_MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le logo dépasse 1 Mo");
        }
        SystemSettings settings = entity();
        StoredFile stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorageService.store("branding", file.getOriginalFilename(), type, in);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Échec de l'enregistrement du logo");
        }
        String previous = settings.getLogoStorageKey();
        settings.setLogo(stored.storageKey(), type);
        deleteQuietly(previous);
        auditLogService.record(AuditAction.SYSTEM_SETTINGS_UPDATED, "SystemSettings", SystemSettings.AUDIT_ENTITY_ID,
                "Nouveau logo de l'association");
        repository.flush();
        reload();
        return get();
    }

    public SettingsResponse removeLogo() {
        SystemSettings settings = entity();
        String previous = settings.getLogoStorageKey();
        if (previous != null) {
            settings.setLogo(null, null);
            repository.flush();
            deleteQuietly(previous);
            auditLogService.record(AuditAction.SYSTEM_SETTINGS_UPDATED, "SystemSettings", SystemSettings.AUDIT_ENTITY_ID,
                    "Logo de l'association remis par défaut");
        }
        reload();
        return get();
    }

    // ---------------------------------------------------------------- interne

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        reload();
    }

    /** Relit la base et applique l'identité de l'association aux PDF. */
    public synchronized Snapshot reload() {
        SystemSettings s = repository.findById(SystemSettings.SINGLETON_ID).orElse(null);
        if (s == null) {
            Snapshot fallback = new Snapshot(AekdPdf.DEFAULT_ASSOCIATION_NAME, AekdPdf.DEFAULT_ASSOCIATION_SHORT,
                    null, null, null, false, null, null, null, null, null, false, null,
                    null, null, null, null, null, null, null, false, 0L);
            snapshot = fallback;
            return fallback;
        }
        Snapshot fresh = new Snapshot(s.getAssociationName(), s.getAssociationShortName(), s.getAssociationAddress(),
                s.getAssociationPhone(), s.getAssociationEmail(), s.getLogoStorageKey() != null, s.getSessionDays(),
                s.getOtpTtlMinutes(), s.getOtpMaxAttempts(), s.getOtpResendSeconds(), s.getOtpMaxPerHour(),
                s.isMaintenanceEnabled(), s.getMaintenanceMessage(),
                s.getThemePrimaryColor(), s.getThemeSecondaryColor(), s.getThemeDarkColor(),
                s.getThemeHeadingFont(), s.getThemeBodyFont(), s.getThemePattern(),
                s.getLoginSlogan(), s.getLoginImageStorageKey() != null,
                s.getUpdatedAt() != null ? s.getUpdatedAt().toEpochMilli() : 0L);
        snapshot = fresh;
        AekdPdf.applyBranding(new AekdPdf.Branding(fresh.associationName(), fresh.associationShortName(),
                contactLine(fresh), logoBytes(s)));
        // Couleurs du thème sur les PDF (decisions.md n° 72) ; null = couleurs AEKD.
        AekdPdf.applyColors(fresh.themePrimaryColor(), fresh.themeSecondaryColor(), fresh.themeDarkColor());
        return fresh;
    }

    private byte[] logoBytes(SystemSettings settings) {
        if (settings.getLogoStorageKey() == null) {
            return null;
        }
        try (InputStream in = fileStorageService.retrieve(settings.getLogoStorageKey())) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("Logo des PDF illisible ({}), logo par défaut utilisé", e.getMessage());
            return null;
        }
    }

    private static String contactLine(Snapshot s) {
        List<String> parts = new ArrayList<>();
        for (String part : new String[]{s.associationAddress(), s.associationPhone(), s.associationEmail()}) {
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private SystemSettings entity() {
        return repository.findById(SystemSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Ligne system_settings absente (migration V32)"));
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            fileStorageService.delete(key);
        } catch (IOException ignored) {
            // Best-effort : un fichier orphelin n'est pas bloquant.
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
