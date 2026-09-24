package cm.aekd.tontine.session;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §6 : la création/gestion des séances est réservée à ADMIN et
 * TRESORIER. La consultation est ouverte à tout utilisateur authentifié
 * (les membres doivent pouvoir voir la séance courante, §25).
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody SessionRequest request) {
        SessionResponse response = sessionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<SessionResponse> findAll() {
        return sessionService.findAll();
    }

    @GetMapping("/{id}")
    public SessionResponse findById(@PathVariable UUID id) {
        return sessionService.findById(id);
    }
}
