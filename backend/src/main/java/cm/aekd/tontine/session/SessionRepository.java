package cm.aekd.tontine.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findAllByOrderBySessionDateDesc();

    /** Séance la plus récente tenue au plus tard à la date donnée. */
    Optional<Session> findFirstBySessionDateLessThanEqualOrderBySessionDateDesc(LocalDate onOrBefore);
}
