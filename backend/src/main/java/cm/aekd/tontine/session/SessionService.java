package cm.aekd.tontine.session;

import cm.aekd.tontine.audit.AuditAction;
import cm.aekd.tontine.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final AuditLogService auditLogService;

    public SessionService(SessionRepository sessionRepository, AuditLogService auditLogService) {
        this.sessionRepository = sessionRepository;
        this.auditLogService = auditLogService;
    }

    public SessionResponse create(SessionRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date de fin ne peut pas précéder la date de début");
        }

        Session session = new Session(request.label(), request.startDate(), request.endDate());
        session = sessionRepository.save(session);

        auditLogService.record(AuditAction.SESSION_CREATED, "Session", session.getId(),
                "Création de la séance \"" + session.getLabel() + "\"");

        return SessionResponse.from(session);
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
