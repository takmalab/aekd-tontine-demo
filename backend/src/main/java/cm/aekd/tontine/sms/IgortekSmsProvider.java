package cm.aekd.tontine.sms;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
 *
 * Une réponse 202 signifie seulement que le message est mis en file
 * d'attente chez Igor : un message bloqué ensuite n'est pas détecté ici.
 */
@Component
public class IgortekSmsProvider implements SmsProvider {

    public static final String ID = "igortek";

    private final String apiKey;
    private final RestClient restClient;

    public IgortekSmsProvider(SmsProperties properties, RestClient.Builder builder) {
        this.apiKey = properties.getIgortek().getApiKey();
        // Délais de connexion et de lecture : spring.http.client.* (application.yml).
        this.restClient = builder
                .baseUrl(properties.getIgortek().getBaseUrl())
                .build();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Igor SMS Gateway";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public void sendLoginCode(String to, String code, Duration validity, String idempotencyKey) {
        if (!isConfigured()) {
            throw new SmsSendException("Igor SMS n'est pas configuré (clé API absente)");
        }
        try {
            restClient.post()
                    .uri("/api/v1/sms/send")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .body(Map.of("to", to, "message", SmsTexts.loginCode(code, validity)))
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
