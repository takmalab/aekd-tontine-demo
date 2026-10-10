package cm.aekd.tontine.tournament;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Tournois (génération automatique des séances) : réservés à ADMIN et TRESORIER, comme les séances. */
@RestController
@RequestMapping("/api/tournaments")
@PreAuthorize("hasAnyRole('ADMIN', 'TRESORIER')")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @GetMapping
    public List<TournamentResponse> findAll() {
        return tournamentService.findAll();
    }

    @GetMapping("/{id}")
    public TournamentResponse findById(@PathVariable UUID id) {
        return tournamentService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TournamentResponse> create(@Valid @RequestBody TournamentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tournamentService.create(request));
    }

    @PutMapping("/{id}")
    public TournamentResponse update(@PathVariable UUID id, @Valid @RequestBody TournamentUpdateRequest request) {
        return tournamentService.update(id, request);
    }

    @PutMapping("/{id}/stop")
    public TournamentResponse stop(@PathVariable UUID id) {
        return tournamentService.stop(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tournamentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
