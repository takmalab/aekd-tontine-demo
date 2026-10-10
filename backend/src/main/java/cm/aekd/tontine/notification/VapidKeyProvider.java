package cm.aekd.tontine.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clés VAPID du serveur. Fournies par l'environnement en production
 * (APP_PUSH_VAPID_PUBLIC_KEY / APP_PUSH_VAPID_PRIVATE_KEY), sinon générées au
 * premier besoin et conservées en base : elles ne doivent plus changer, sinon
 * les abonnements existants des navigateurs deviennent invalides.
 */
@Component
public class VapidKeyProvider {

    private final NotificationSettingsRepository settingsRepository;
    private final String configuredPublicKey;
    private final String configuredPrivateKey;
    private final String subject;

    private volatile WebPushCrypto.VapidKeys cached;

    public VapidKeyProvider(NotificationSettingsRepository settingsRepository,
                            @Value("${app.push.vapid.public-key:}") String configuredPublicKey,
                            @Value("${app.push.vapid.private-key:}") String configuredPrivateKey,
                            @Value("${app.push.subject:mailto:admin@aekd.cm}") String subject) {
        this.settingsRepository = settingsRepository;
        this.configuredPublicKey = configuredPublicKey;
        this.configuredPrivateKey = configuredPrivateKey;
        this.subject = subject;
    }

    /** Génère les clés dès le démarrage, plutôt qu'au premier envoi (évite deux générations concurrentes). */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        keys();
    }

    /** Contact transmis aux services push des navigateurs (« mailto: » ou URL). */
    public String subject() {
        return subject;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized WebPushCrypto.VapidKeys keys() {
        if (cached != null) {
            return cached;
        }
        if (!configuredPublicKey.isBlank() && !configuredPrivateKey.isBlank()) {
            cached = new WebPushCrypto.VapidKeys(configuredPublicKey, configuredPrivateKey);
            return cached;
        }
        NotificationSettings settings = settingsRepository.getSettings();
        if (settings.getVapidPublicKey() == null || settings.getVapidPrivateKey() == null) {
            WebPushCrypto.VapidKeys generated = WebPushCrypto.generateVapidKeys();
            settings.setVapidKeys(generated.publicKey(), generated.privateKey());
            settingsRepository.save(settings);
        }
        cached = new WebPushCrypto.VapidKeys(settings.getVapidPublicKey(), settings.getVapidPrivateKey());
        return cached;
    }
}
