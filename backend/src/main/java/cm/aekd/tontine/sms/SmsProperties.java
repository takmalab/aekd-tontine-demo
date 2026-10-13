package cm.aekd.tontine.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de l'envoi de SMS. Les clés API n'ont pas de valeur par
 * défaut : elles sont fournies par variable d'environnement ou par un
 * fichier .env local, ignoré par Git (voir application.yml).
 */
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    /**
     * Fournisseur utilisé tant qu'aucun n'a été choisi dans l'écran
     * « Paramètres SMS » : "log" (simulation), "igortek" ou "scmchost".
     */
    private String provider = LogSmsProvider.ID;

    private final Igortek igortek = new Igortek();

    private final Scmchost scmchost = new Scmchost();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Igortek getIgortek() {
        return igortek;
    }

    public Scmchost getScmchost() {
        return scmchost;
    }

    public static class Igortek {

        private String baseUrl = "https://api.sms.igortek.com";
        private String apiKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Scmchost {

        private String baseUrl = "https://api.scmchost.com";
        private String apiKey;
        /** Modèle approuvé utilisé pour les codes de connexion. */
        private String otpTemplateId;
        /** Variable du modèle qui reçoit le code (ex. {CODE}). */
        private String codeVariable = "CODE";
        /** Variable qui reçoit la durée de validité en minutes (ex. {TEMPS}) ; vide si le modèle n'en a pas. */
        private String validityVariable = "TEMPS";
        /** Variable qui reçoit le nom de l'application (ex. {NOM_APP}) ; vide si le modèle n'en a pas. */
        private String appNameVariable = "NOM_APP";
        private String appName = "AEKD Tontine";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getOtpTemplateId() {
            return otpTemplateId;
        }

        public void setOtpTemplateId(String otpTemplateId) {
            this.otpTemplateId = otpTemplateId;
        }

        public String getCodeVariable() {
            return codeVariable;
        }

        public void setCodeVariable(String codeVariable) {
            this.codeVariable = codeVariable;
        }

        public String getValidityVariable() {
            return validityVariable;
        }

        public void setValidityVariable(String validityVariable) {
            this.validityVariable = validityVariable;
        }

        public String getAppNameVariable() {
            return appNameVariable;
        }

        public void setAppNameVariable(String appNameVariable) {
            this.appNameVariable = appNameVariable;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }
    }
}
