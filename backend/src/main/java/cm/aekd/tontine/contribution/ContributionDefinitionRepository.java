package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContributionDefinitionRepository extends JpaRepository<ContributionDefinition, UUID> {
}
