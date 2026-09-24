package cm.aekd.tontine.sanction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SanctionRuleRepository extends JpaRepository<SanctionRule, UUID> {

    List<SanctionRule> findByContributionDefinitionId(UUID contributionDefinitionId);
}
