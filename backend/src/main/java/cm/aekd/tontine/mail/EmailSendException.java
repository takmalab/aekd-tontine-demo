package cm.aekd.tontine.mail;

/** L'e-mail n'a pas pu être remis au service d'envoi. */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
