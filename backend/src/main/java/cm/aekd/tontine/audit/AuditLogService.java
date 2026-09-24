package cm.aekd.tontine.audit;

import cm.aekd.tontine.common.CurrentUserProvider;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Enregistrement et consultation du journal d'audit (CLAUDE.md §26).
 * {@link #record} ne lève jamais d'exception métier : une trace d'audit
 * manquante ne doit jamais faire échouer l'opération qu'elle documente.
 * Si aucun utilisateur n'est résolvable (cas théorique), l'entrée est
 * tout de même écrite avec un utilisateur nul plutôt que d'être perdue.
 */
@Service
@Transactional
public class AuditLogService {

    private final AuditLogRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository repository, CurrentUserProvider currentUserProvider,
                            UserRepository userRepository) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    public void record(AuditAction action, String entityType, UUID entityId, String description) {
        User user = currentUserProvider.currentUserId().flatMap(userRepository::findById).orElse(null);
        repository.save(new AuditLog(user, action, entityType, entityId, description));
    }

    public List<AuditLogResponse> findAll() {
        return repository.findAllByOrderByOccurredAtDesc().stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    public List<AuditLogResponse> findByEntity(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
