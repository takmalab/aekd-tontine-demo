package cm.aekd.tontine.sms;

import java.time.Duration;

/**
 * Un fournisseur d'envoi de SMS. Tous les fournisseurs sont chargés ; celui
 * qui est utilisé se choisit dans l'écran « Paramètres SMS » (ADMIN), voir
 * {@link SmsRouter} et docs/decisions.md n° 48.
 *
 * L'envoi porte sur un code de connexion et non sur un texte libre : certains
 * fournisseurs (SCMC Host) n'envoient que des modèles validés à l'avance.
 */
public interface SmsProvider {

    /** Identifiant stable, enregistré en base (ex. "igortek"). */
    String id();

    /** Nom affiché dans l'interface. */
    String label();

    /** Faux si la configuration nécessaire (clé API...) est absente. */
    boolean isConfigured();

    /** Vrai pour un fournisseur qui n'envoie pas réellement de SMS (simulation). */
    default boolean isSimulation() {
        return false;
    }

    /**
     * @param to             destinataire au format E.164 (+237XXXXXXXXX)
     * @param code           code de connexion à transmettre
     * @param validity       durée de validité du code, à indiquer si le message le permet
     * @param idempotencyKey clé évitant un double envoi si la requête est rejouée
     * @throws SmsSendException si le fournisseur n'est pas configuré, refuse
     *                          l'envoi ou n'est pas joignable
     */
    void sendLoginCode(String to, String code, Duration validity, String idempotencyKey);
}
