package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContributionTransactionRepository extends JpaRepository<ContributionTransaction, UUID> {

    List<ContributionTransaction> findByStatus(ContributionTransactionStatus status);

    List<ContributionTransaction> findByMemberId(UUID memberId);

    boolean existsByMemberIdAndContributionPeriodIdAndStatusIn(UUID memberId, UUID contributionPeriodId,
                                                                 List<ContributionTransactionStatus> statuses);
}
