package cm.aekd.tontine.sms;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.common.PhoneNumbers;
import cm.aekd.tontine.sms.SmsSettingsDtos.ProviderResponse;
import cm.aekd.tontine.sms.SmsSettingsDtos.SettingsRequest;
import cm.aekd.tontine.sms.SmsSettingsDtos.SettingsResponse;
import cm.aekd.tontine.sms.SmsSettingsDtos.TestRequest;
import cm.aekd.tontine.sms.SmsSettingsDtos.TestResponse;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * Écran « Paramètres SMS » (ADMIN, docs/decisions.md n° 48) : choix du
 * fournisseur actif, secours automatique et SMS de test. Les clés API ne
 * passent jamais par ici : elles restent dans la configuration du serveur.
 */
@Service
@Transactional
public class SmsSettingsService {

    private final SmsSettingsRepository settingsRepository;
    private final SmsRouter router;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final SecureRandom random = new SecureRandom();

    public SmsSettingsService(SmsSettingsRepository settingsRepository, SmsRouter router,
                              UserRepository userRepository, CurrentUserProvider currentUserProvider,
                              AuditLogService auditLogService) {
        this.settingsRepository = settingsRepository;
        this.router = router;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        SmsSettings settings = settingsRepository.getSingleton();
        return new SettingsResponse(
                router.activeProvider().id(),
                settings.getActiveProvider() == null,
                settings.isFallbackEnabled(),
                router.providers().stream().map(ProviderResponse::from).toList(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy() != null ? settings.getUpdatedBy().getEmail() : null);
    }

    public SettingsResponse update(SettingsRequest request) {
        SmsProvider provider = configuredProvider(request.activeProvider());
        SmsSettings settings = settingsRepository.getSingleton();
        String previous = router.activeProvider().label();
        settings.setActiveProvider(provider.id());
        settings.setFallbackEnabled(request.fallbackEnabled());
        settings.setUpdatedBy(currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null));
        settingsRepository.save(settings);

        auditLogService.record(AuditAction.SMS_SETTINGS_UPDATED, "SmsSettings", SmsSettings.AUDIT_ENTITY_ID,
                "Fournisseur SMS : " + previous + " -> " + provider.label() + " ; secours automatique "
                        + (request.fallbackEnabled() ? "activé" : "désactivé"));
        return get();
    }

    /** Envoie un code de test par un fournisseur précis, sans secours, pour vérifier qu'il délivre. */
    public TestResponse sendTest(TestRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        SmsProvider provider = request.provider() == null || request.provider().isBlank()
                ? router.activeProvider()
                : configuredProvider(request.provider());
        String code = String.format("%06d", random.nextInt(1_000_000));

        try {
            provider.sendLoginCode(phone, code, Duration.ofMinutes(5), "test-" + UUID.randomUUID());
        } catch (SmsSendException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    provider.label() + " : " + e.getMessage());
        }

        auditLogService.record(AuditAction.SMS_TEST_SENT, "SmsSettings", SmsSettings.AUDIT_ENTITY_ID,
                "SMS de test envoyé par " + provider.label() + " au " + phone);
        String message = provider.isSimulation()
                ? "Simulation : aucun SMS réel, le message est écrit dans le journal du serveur."
                : "SMS accepté par " + provider.label() + ". Vérifiez qu'il arrive bien sur le téléphone.";
        return new TestResponse(provider.id(), phone, code, message);
    }

    private SmsProvider configuredProvider(String id) {
        SmsProvider provider = router.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Fournisseur SMS inconnu : " + id));
        if (!provider.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    provider.label() + " n'est pas configuré sur le serveur (clé API manquante)");
        }
        return provider;
    }
}
