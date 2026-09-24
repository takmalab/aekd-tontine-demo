package cm.aekd.tontine.sanction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppliedSanctionRepository extends JpaRepository<AppliedSanction, UUID> {

    List<AppliedSanction> findByMemberId(UUID memberId);

    List<AppliedSanction> findByContributionPeriod_ContributionDefinition_Id(UUID contributionDefinitionId);

    boolean existsBySanctionRuleIdAndMemberIdAndContributionPeriodIdAndStatus(
            UUID sanctionRuleId, UUID memberId, UUID contributionPeriodId, AppliedSanctionStatus status);
}
