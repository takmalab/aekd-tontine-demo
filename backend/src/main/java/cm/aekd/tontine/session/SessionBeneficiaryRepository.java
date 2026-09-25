package cm.aekd.tontine.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SessionBeneficiaryRepository extends JpaRepository<SessionBeneficiary, UUID> {

    List<SessionBeneficiary> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<SessionBeneficiary> findBySessionIdInOrderByCreatedAtAsc(Collection<UUID> sessionIds);
}
