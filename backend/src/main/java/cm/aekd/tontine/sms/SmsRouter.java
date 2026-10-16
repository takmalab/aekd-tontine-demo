package cm.aekd.tontine.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Choisit le fournisseur SMS (docs/decisions.md n° 48) : celui choisi dans
 * l'écran « Paramètres SMS », à défaut celui de la configuration
 * ({@code app.sms.provider}). Si le secours automatique est activé et que
 * le fournisseur actif échoue, les autres fournisseurs réels configurés
 * sont essayés. La simulation n'est jamais utilisée comme secours : un
 * code « envoyé » dans le journal ne parviendrait pas à l'utilisateur.
 *
 * Limite : un SMS accepté par le fournisseur puis bloqué chez lui n'est pas
 * un échec visible ici ; la bascule manuelle reste alors nécessaire.
 */
@Service
public class SmsRouter implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsRouter.class);

    private final List<SmsProvider> providers;
    private final SmsSettingsRepository settingsRepository;
    private final SmsProperties properties;

    public SmsRouter(List<SmsProvider> providers, SmsSettingsRepository settingsRepository,
                     SmsProperties properties) {
        this.providers = providers.stream().sorted(Comparator.comparing(SmsProvider::id)).toList();
        this.settingsRepository = settingsRepository;
        this.properties = properties;
    }

    public List<SmsProvider> providers() {
        return providers;
    }

    public Optional<SmsProvider> find(String id) {
        return providers.stream().filter(provider -> provider.id().equals(id)).findFirst();
    }

    /** Fournisseur actif ; un identifiant inconnu retombe sur la simulation. */
    public SmsProvider activeProvider() {
        String id = Optional.ofNullable(settingsRepository.getSingleton().getActiveProvider())
                .orElse(properties.getProvider());
        return find(id).orElseGet(() -> {
            log.warn("Fournisseur SMS inconnu \"{}\" : simulation utilisée", id);
            return find(LogSmsProvider.ID).orElseThrow();
        });
    }

    @Override
    public void sendLoginCode(String to, String code, Duration validity, String idempotencyKey) {
        List<SmsProvider> chain = chain();
        SmsSendException lastFailure = null;
        for (SmsProvider provider : chain) {
            try {
                provider.sendLoginCode(to, code, validity, idempotencyKey);
                if (lastFailure != null) {
                    log.warn("Code de connexion envoyé vers {} par le fournisseur de secours {}", to, provider.id());
                }
                return;
            } catch (SmsSendException e) {
                log.error("Échec d'envoi SMS par {} vers {} : {}", provider.id(), to, e.getMessage());
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    /** Notification en texte libre : seuls les fournisseurs qui le permettent sont essayés (n° 58). */
    @Override
    public void sendText(String to, String text, String idempotencyKey) {
        List<SmsProvider> chain = chain().stream().filter(SmsProvider::supportsText).toList();
        if (chain.isEmpty()) {
            throw new SmsSendException("Aucun fournisseur SMS actif ne permet d'envoyer une notification");
        }
        SmsSendException lastFailure = null;
        for (SmsProvider provider : chain) {
            try {
                provider.sendText(to, text, idempotencyKey);
                return;
            } catch (SmsSendException e) {
                log.error("Échec d'envoi de notification SMS par {} vers {} : {}", provider.id(), to, e.getMessage());
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private List<SmsProvider> chain() {
        SmsProvider active = activeProvider();
        List<SmsProvider> chain = new ArrayList<>(List.of(active));
        if (settingsRepository.getSingleton().isFallbackEnabled() && !active.isSimulation()) {
            providers.stream()
                    .filter(provider -> provider != active && !provider.isSimulation() && provider.isConfigured())
                    .forEach(chain::add);
        }
        return chain;
    }
}
