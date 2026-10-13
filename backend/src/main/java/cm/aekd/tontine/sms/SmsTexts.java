package cm.aekd.tontine.sms;

import java.time.Duration;

/** Textes des SMS pour les fournisseurs qui acceptent un texte libre. */
final class SmsTexts {

    private SmsTexts() {
    }

    /**
     * Sans accents : un seul caractère hors alphabet GSM ferait passer le SMS en
     * Unicode (70 caractères au lieu de 160) et doublerait son coût.
     */
    static String loginCode(String code, Duration validity) {
        return "AEKD Tontine : votre code de connexion est " + code + ". Il expire dans "
                + validity.toMinutes() + " min. Ne le communiquez a personne.";
    }
}
