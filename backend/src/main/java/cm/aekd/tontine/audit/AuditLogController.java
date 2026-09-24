package cm.aekd.tontine.audit;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * CLAUDE.md §6 : "consulter les audits" n'apparaît que dans la liste des
 * permissions ADMIN, pas dans celle du TRESORIER.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuditLogResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{entityType}/{entityId}")
    public List<AuditLogResponse> findByEntity(@PathVariable String entityType, @PathVariable UUID entityId) {
        return service.findByEntity(entityType, entityId);
    }
}
