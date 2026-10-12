package cm.aekd.tontine.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fournisseur par défaut : aucun SMS réel n'est envoyé, le message (code
 * OTP compris) est écrit dans le journal du backend. Réservé au
 * développement et à la démonstration.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String to, String message, String idempotencyKey) {
        log.warn("SMS SIMULE (app.sms.provider=log) vers {} : {}", to, message);
    }
}
