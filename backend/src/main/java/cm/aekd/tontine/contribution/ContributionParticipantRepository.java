package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionParticipantRepository extends JpaRepository<ContributionParticipant, UUID> {

    List<ContributionParticipant> findByContributionDefinitionId(UUID contributionDefinitionId);

    List<ContributionParticipant> findByMemberId(UUID memberId);

    Optional<ContributionParticipant> findByContributionDefinitionIdAndMemberId(UUID contributionDefinitionId,
                                                                               UUID memberId);

    boolean existsByContributionDefinitionIdAndMemberId(UUID contributionDefinitionId, UUID memberId);

    /** Participants concernés par cette séance (décision n° 60 : ajout tardif). */
    default List<ContributionParticipant> findForPeriod(ContributionPeriod period) {
        return findByContributionDefinitionId(period.getContributionDefinition().getId()).stream()
                .filter(participant -> participant.appliesTo(period))
                .toList();
    }

    /** Le membre participe-t-il à la cotisation pour cette séance (décision n° 60) ? */
    default boolean participatesIn(ContributionPeriod period, UUID memberId) {
        return findByContributionDefinitionIdAndMemberId(period.getContributionDefinition().getId(), memberId)
                .filter(participant -> participant.appliesTo(period))
                .isPresent();
    }
}
