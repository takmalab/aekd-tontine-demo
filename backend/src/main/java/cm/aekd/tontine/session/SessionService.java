package cm.aekd.tontine.session;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public SessionResponse create(SessionRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de fin ne peut pas précéder la date de début");
        }

        Session session = new Session(request.label(), request.startDate(), request.endDate());
        return SessionResponse.from(sessionRepository.save(session));
    }

    public List<SessionResponse> findAll() {
        return sessionRepository.findAllByOrderByStartDateDesc().stream()
                .map(SessionResponse::from)
                .toList();
    }

    public SessionResponse findById(UUID id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        return SessionResponse.from(session);
    }
}
