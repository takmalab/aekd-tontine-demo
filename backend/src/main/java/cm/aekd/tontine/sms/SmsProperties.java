package cm.aekd.tontine.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de l'envoi de SMS. La clé API n'a pas de valeur par
 * défaut : elle est fournie uniquement par variable d'environnement
 * ({@code IGORTEK_SMS_API_KEY}).
 */
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    /** "log" (simulation, par défaut) ou "igortek". */
    private String provider = "log";

    private final Igortek igortek = new Igortek();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Igortek getIgortek() {
        return igortek;
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
}
