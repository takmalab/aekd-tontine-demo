package cm.aekd.tontine.tournament;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {

    List<Tournament> findAllByOrderByCreatedAtDesc();

    List<Tournament> findByStatus(TournamentStatus status);

    boolean existsByStatus(TournamentStatus status);
}
