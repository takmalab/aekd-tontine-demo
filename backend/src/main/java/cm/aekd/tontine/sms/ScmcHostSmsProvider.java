package cm.aekd.tontine.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCMC Host (https://sms.scmchost.com/docs/) : tout envoi passe par un modèle
 * approuvé. {@code POST /messages/send} avec l'en-tête {@code X-API-Key}, le
 * corps {@code {"template_id", "recipients": [...], "variables": {"CODE": ...}}}
 * et {@code Idempotency-Key}. Réponse
 * {@code {"success", "data": {"sent", "failed", "results": [{"recipient", "status", "reason"}]}}} ;
 * erreurs {@code {"success": false, "error": "..."}} avec 400, 401, 402 (solde
 * insuffisant), 403, 404 (modèle introuvable), 429.
 */
@Component
public class ScmcHostSmsProvider implements SmsProvider {

    public static final String ID = "scmchost";

    private final SmsProperties.Scmchost config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ScmcHostSmsProvider(SmsProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.config = properties.getScmchost();
        this.objectMapper = objectMapper;
        // Délais de connexion et de lecture : spring.http.client.* (application.yml).
        this.restClient = builder
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "SCMC Host";
    }

    @Override
    public boolean isConfigured() {
        return notBlank(config.getApiKey()) && notBlank(config.getOtpTemplateId());
    }

    @Override
    public void sendLoginCode(String to, String code, Duration validity, String idempotencyKey) {
        if (!isConfigured()) {
            throw new SmsSendException("SCMC Host n'est pas configuré (clé API ou modèle OTP absent)");
        }
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/messages/send")
                    .header("X-API-Key", config.getApiKey())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "template_id", config.getOtpTemplateId(),
                            "recipients", List.of(to),
                            "variables", variables(code, validity)))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new SmsSendException(describe(e.getStatusCode(), errorOf(e.getResponseBodyAsString()))
                    + " (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new SmsSendException("SCMC Host injoignable", e);
        }
        checkAccepted(response);
    }

    @Override
    public boolean supportsText() {
        return notBlank(config.getApiKey()) && notBlank(config.getNotifyTemplateId());
    }

    /** Notification : modèle approuvé « notification », le texte dans sa variable (n° 58). */
    @Override
    public void sendText(String to, String text, String idempotencyKey) {
        if (!supportsText()) {
            throw new SmsSendException("SCMC Host : aucun modèle de notification configuré (SCMCHOST_NOTIFY_TEMPLATE_ID)");
        }
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put(config.getNotifyMessageVariable(), text);
        if (notBlank(config.getAppNameVariable())) {
            variables.put(config.getAppNameVariable(), config.getAppName());
        }
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/messages/send")
                    .header("X-API-Key", config.getApiKey())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "template_id", config.getNotifyTemplateId(),
                            "recipients", List.of(to),
                            "variables", variables))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new SmsSendException(describe(e.getStatusCode(), errorOf(e.getResponseBodyAsString()))
                    + " (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new SmsSendException("SCMC Host injoignable", e);
        }
        checkAccepted(response);
    }

    /**
     * Variables du modèle : le code, et selon le modèle la durée de validité et le
     * nom de l'application (ex. « OTP Universel » : {NOM_APP}, {CODE}, {TEMPS}).
     */
    private Map<String, String> variables(String code, Duration validity) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put(config.getCodeVariable(), code);
        if (notBlank(config.getValidityVariable())) {
            variables.put(config.getValidityVariable(), String.valueOf(validity.toMinutes()));
        }
        if (notBlank(config.getAppNameVariable())) {
            variables.put(config.getAppNameVariable(), config.getAppName());
        }
        return variables;
    }

    /** Un HTTP 200 peut contenir un refus par destinataire (numéro invalide, pas de route...). */
    private static void checkAccepted(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false)) {
            throw new SmsSendException("Envoi refusé par SCMC Host"
                    + (response != null && response.hasNonNull("error") ? " : " + response.get("error").asText() : ""));
        }
        JsonNode data = response.path("data");
        if (data.path("failed").asInt(0) > 0 || data.path("sent").asInt(0) == 0) {
            JsonNode first = data.path("results").path(0);
            String reason = first.hasNonNull("reason") ? first.get("reason").asText() : first.path("status").asText("inconnu");
            throw new SmsSendException("SMS refusé par SCMC Host : " + reason);
        }
    }

    private String errorOf(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.hasNonNull("error") ? node.get("error").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String describe(HttpStatusCode status, String error) {
        String base = switch (status.value()) {
            case 401 -> "Clé API SCMC Host absente, invalide ou révoquée";
            case 402 -> "Solde SCMC Host insuffisant";
            case 404 -> "Modèle OTP SCMC Host introuvable";
            case 429 -> "Trop d'envois vers SCMC Host, réessayez plus tard";
            default -> "Envoi refusé par SCMC Host";
        };
        return error != null ? base + " : " + error : base;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
