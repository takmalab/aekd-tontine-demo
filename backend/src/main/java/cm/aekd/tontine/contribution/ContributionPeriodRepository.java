package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContributionPeriodRepository extends JpaRepository<ContributionPeriod, UUID> {

    List<ContributionPeriod> findByContributionDefinitionId(UUID contributionDefinitionId);

    List<ContributionPeriod> findBySessionId(UUID sessionId);

    boolean existsByContributionDefinitionIdAndSessionId(UUID contributionDefinitionId, UUID sessionId);
}
