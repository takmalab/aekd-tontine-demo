package cm.aekd.tontine.sms;

/** Échec d'envoi d'un SMS (fournisseur injoignable, clé invalide, quota épuisé...). */
public class SmsSendException extends RuntimeException {

    public SmsSendException(String message) {
        super(message);
    }

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
