package cm.aekd.tontine.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Simulation : aucun SMS réel n'est envoyé, le message (code OTP compris)
 * est écrit dans le journal du backend. Réservé au développement et à la
 * démonstration.
 */
@Component
public class LogSmsProvider implements SmsProvider {

    public static final String ID = "log";

    private static final Logger log = LoggerFactory.getLogger(LogSmsProvider.class);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Simulation (journal du serveur)";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean isSimulation() {
        return true;
    }

    @Override
    public void sendLoginCode(String to, String code, Duration validity, String idempotencyKey) {
        log.warn("SMS SIMULE (fournisseur log) vers {} : {}", to, SmsTexts.loginCode(code, validity));
    }
}
