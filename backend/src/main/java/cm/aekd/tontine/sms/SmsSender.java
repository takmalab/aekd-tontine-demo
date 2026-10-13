package cm.aekd.tontine.sms;

import java.time.Duration;

/**
 * Envoi de SMS vu par le reste de l'application. L'implémentation
 * {@link SmsRouter} choisit le fournisseur ({@link SmsProvider}) actif et,
 * si le secours automatique est activé, essaie les autres en cas d'échec.
 */
public interface SmsSender {

    /**
     * @param to             destinataire au format E.164 (+237XXXXXXXXX)
     * @param code           code de connexion à transmettre
     * @param validity       durée de validité du code
     * @param idempotencyKey clé évitant un double envoi si la requête est rejouée
     * @throws SmsSendException si aucun fournisseur n'a pu envoyer le SMS
     */
    void sendLoginCode(String to, String code, Duration validity, String idempotencyKey);
}
