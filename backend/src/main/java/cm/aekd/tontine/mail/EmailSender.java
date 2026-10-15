package cm.aekd.tontine.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Envoi des e-mails (docs/decisions.md n° 55).
 * <ul>
 *   <li>{@code brevo} : API Brevo, {@code POST /v3/smtp/email} avec l'en-tête
 *   {@code api-key} (https://developers.brevo.com/reference/sendtransacemail) ;</li>
 *   <li>{@code log} (par défaut) : simulation, l'e-mail est écrit dans les
 *   journaux du serveur, pour le développement et tant que Brevo n'est pas
 *   configuré.</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(EmailProperties.class)
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final EmailProperties properties;
    private final RestClient restClient;

    public EmailSender(EmailProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        // Délais de connexion et de lecture : spring.http.client.* (application.yml).
        this.restClient = builder.baseUrl(properties.getBrevo().getBaseUrl()).build();
    }

    /** Vrai si les e-mails partent réellement (et non en simulation). */
    public boolean isLive() {
        return "brevo".equalsIgnoreCase(properties.getProvider())
                && !properties.getBrevo().getApiKey().isBlank()
                && !properties.getFromAddress().isBlank();
    }

    public void send(String to, String subject, String text, String html) {
        if (!isLive()) {
            log.info("[E-MAIL SIMULÉ] À : {} | Objet : {}\n{}", to, subject, text);
            return;
        }
        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", properties.getBrevo().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sender", Map.of("name", properties.getFromName(), "email", properties.getFromAddress()),
                            "to", List.of(Map.of("email", to)),
                            "subject", subject,
                            "textContent", text,
                            "htmlContent", html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new EmailSendException("Brevo a refusé l'e-mail (HTTP " + e.getStatusCode().value() + ") : "
                    + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new EmailSendException("Brevo injoignable", e);
        }
    }
}
