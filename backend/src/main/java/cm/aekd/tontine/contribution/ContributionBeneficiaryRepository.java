package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContributionBeneficiaryRepository extends JpaRepository<ContributionBeneficiary, UUID> {

    List<ContributionBeneficiary> findByContributionDefinitionId(UUID contributionDefinitionId);

    boolean existsByContributionDefinitionIdAndMemberId(UUID contributionDefinitionId, UUID memberId);
}
