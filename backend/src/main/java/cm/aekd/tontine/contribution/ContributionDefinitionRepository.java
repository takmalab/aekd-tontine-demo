package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ContributionDefinitionRepository extends JpaRepository<ContributionDefinition, UUID> {

    /**
     * Suppression directe en base : les lignes dépendantes (périodes, participants,
     * bénéficiaires, règles de sanction…) sont supprimées par ON DELETE CASCADE.
     * Le contexte de persistance est vidé pour ne pas garder d'entités supprimées.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ContributionDefinition e WHERE e.id = :id")
    void deleteCascadeById(@Param("id") UUID id);
}
