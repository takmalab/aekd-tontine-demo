package cm.aekd.tontine.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renvoie au client le message des {@link ResponseStatusException} levées par les
 * services (ex. « Impossible de supprimer cette séance : des paiements… »).
 * Spring Boot masque par défaut tous les messages d'erreur ; ce gestionnaire
 * n'expose que ces messages métier, rédigés pour l'utilisateur, et laisse les
 * autres erreurs (techniques) sans détail.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", ex.getStatusCode().value());
        body.put("error", status != null ? status.getReasonPhrase() : "");
        body.put("message", ex.getReason() != null ? ex.getReason() : "");
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(body);
    }
}
