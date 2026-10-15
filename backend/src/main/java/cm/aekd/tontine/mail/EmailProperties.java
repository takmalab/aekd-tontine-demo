package cm.aekd.tontine.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Envoi des e-mails (docs/decisions.md n° 55). {@code provider} : {@code log}
 * (simulation : l'e-mail est écrit dans les journaux) ou {@code brevo}.
 */
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private String provider = "log";
    private String fromAddress = "";
    private String fromName = "AEKD Tontine";
    private final Brevo brevo = new Brevo();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public Brevo getBrevo() {
        return brevo;
    }

    public static class Brevo {

        private String baseUrl = "https://api.brevo.com";
        private String apiKey = "";

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
