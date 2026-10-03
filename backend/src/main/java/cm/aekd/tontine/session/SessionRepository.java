package cm.aekd.tontine.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findAllByOrderBySessionDateDesc();

    /** Séance la plus récente tenue au plus tard à la date donnée. */
    Optional<Session> findFirstBySessionDateLessThanEqualOrderBySessionDateDesc(LocalDate onOrBefore);

    /**
     * Suppression directe en base : les lignes dépendantes (périodes, participants,
     * bénéficiaires, règles de sanction…) sont supprimées par ON DELETE CASCADE.
     * Le contexte de persistance est vidé pour ne pas garder d'entités supprimées.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Session e WHERE e.id = :id")
    void deleteCascadeById(@Param("id") UUID id);
}
