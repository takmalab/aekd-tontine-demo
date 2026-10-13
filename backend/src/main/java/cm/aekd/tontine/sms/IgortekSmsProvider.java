package cm.aekd.tontine.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Igor SMS Gateway (https://sms.igortek.com/fr/docs) :
 * {@code POST /api/v1/sms/send} avec {@code Authorization: Bearer <clé>}
 * et le corps {@code {"to": "+237...", "message": "..."}} ; réponse 202.
 * Erreurs documentées : 401 clé invalide, 403 abonnement inactif ou quota
 * épuisé, 429 trop de requêtes.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "igortek")
public class IgortekSmsSender implements SmsSender {

    private final RestClient restClient;

    public IgortekSmsSender(SmsProperties properties, RestClient.Builder builder) {
        String apiKey = properties.getIgortek().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "app.sms.provider=igortek mais aucune clé API : définissez IGORTEK_SMS_API_KEY");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = builder
                .baseUrl(properties.getIgortek().getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public void send(String to, String message, String idempotencyKey) {
        try {
            restClient.post()
                    .uri("/api/v1/sms/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(Map.of("to", to, "message", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new SmsSendException(describe(e.getStatusCode()) + " (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new SmsSendException("Igor SMS Gateway injoignable", e);
        }
    }

    private static String describe(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> "Clé API Igor SMS invalide ou révoquée";
            case 403 -> "Abonnement Igor SMS inactif ou quota épuisé";
            case 429 -> "Trop d'envois vers Igor SMS, réessayez plus tard";
            default -> "Envoi refusé par Igor SMS";
        };
    }
}
