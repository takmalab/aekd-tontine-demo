package cm.aekd.tontine.sms;

/**
 * Envoi de SMS, indépendant du fournisseur (même principe que
 * PaymentService / FileStorageService) :
 * <ul>
 *   <li>{@link LogSmsSender} : simulation, le message est écrit dans le
 *   journal du backend (développement, démonstration, tests) ;</li>
 *   <li>{@link IgortekSmsSender} : Igor SMS Gateway (production).</li>
 * </ul>
 * Le fournisseur est choisi par {@code app.sms.provider}.
 */
public interface SmsSender {

    /**
     * @param to             destinataire au format E.164 (+237XXXXXXXXX)
     * @param message        texte du SMS
     * @param idempotencyKey clé évitant un double envoi si la requête est rejouée
     * @throws SmsSendException si le fournisseur refuse ou n'est pas joignable
     */
    void send(String to, String message, String idempotencyKey);
}
