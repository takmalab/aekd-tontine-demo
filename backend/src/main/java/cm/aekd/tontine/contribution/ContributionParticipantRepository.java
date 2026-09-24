package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContributionParticipantRepository extends JpaRepository<ContributionParticipant, UUID> {

    List<ContributionParticipant> findByContributionDefinitionId(UUID contributionDefinitionId);

    boolean existsByContributionDefinitionIdAndMemberId(UUID contributionDefinitionId, UUID memberId);
}
