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

    /**
     * Texte libre d'une notification (decisions.md n° 58), par les fournisseurs qui
     * le permettent.
     *
     * @throws SmsSendException si aucun fournisseur n'a pu l'envoyer
     */
    default void sendText(String to, String text, String idempotencyKey) {
        throw new SmsSendException("Envoi de texte libre non pris en charge");
    }
}
