package cm.aekd.tontine.contribution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ContributionTransactionRepository extends JpaRepository<ContributionTransaction, UUID> {

    List<ContributionTransaction> findByStatus(ContributionTransactionStatus status);

    List<ContributionTransaction> findByMemberId(UUID memberId);

    boolean existsByMemberIdAndContributionPeriodIdAndStatusIn(UUID memberId, UUID contributionPeriodId,
                                                                 List<ContributionTransactionStatus> statuses);

    boolean existsByMemberIdAndContributionPeriodIdAndStatus(UUID memberId, UUID contributionPeriodId,
                                                               ContributionTransactionStatus status);

    List<ContributionTransaction> findByContributionPeriodIdAndStatus(UUID contributionPeriodId,
                                                                        ContributionTransactionStatus status);

    List<ContributionTransaction> findByContributionPeriodIdAndStatusIn(UUID contributionPeriodId,
                                                                          List<ContributionTransactionStatus> statuses);

    /** Somme des montants d'un membre pour une période, pour un statut donné (paiements partiels). */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ContributionTransaction t "
            + "WHERE t.member.id = :memberId AND t.contributionPeriod.id = :periodId AND t.status = :status")
    BigDecimal sumAmountByMemberAndPeriodAndStatus(@Param("memberId") UUID memberId,
                                                    @Param("periodId") UUID periodId,
                                                    @Param("status") ContributionTransactionStatus status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ContributionTransaction t "
            + "WHERE t.status = :status AND t.contributionPeriod.contributionDefinition.fundDestination = :fundDestination")
    BigDecimal sumAmountByStatusAndFundDestination(@Param("status") ContributionTransactionStatus status,
                                                    @Param("fundDestination") FundDestination fundDestination);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ContributionTransaction t "
            + "WHERE t.status = :status AND t.contributionPeriod.contributionDefinition.fundDestination = :fundDestination "
            + "AND t.member.id = :memberId")
    BigDecimal sumAmountByStatusAndFundDestinationAndMember(@Param("status") ContributionTransactionStatus status,
                                                             @Param("fundDestination") FundDestination fundDestination,
                                                             @Param("memberId") UUID memberId);

    /** Au moins un paiement (quel que soit son statut) sur une période de la cotisation. */
    boolean existsByContributionPeriod_ContributionDefinition_Id(UUID contributionDefinitionId);

    /** Au moins un paiement (quel que soit son statut) sur une période de la séance. */
    boolean existsByContributionPeriod_Session_Id(UUID sessionId);

    /** Au moins un paiement (quel que soit son statut) sur cette période. */
    boolean existsByContributionPeriod_Id(UUID contributionPeriodId);

    /** Cotisations ayant au moins un paiement (quel que soit son statut). */
    @Query("SELECT DISTINCT t.contributionPeriod.contributionDefinition.id FROM ContributionTransaction t")
    Set<UUID> findContributionDefinitionIdsWithPayments();

    /** Paiements d'une destination (ex. épargne individuelle via « Banque / Épargne »), tous statuts. */
    List<ContributionTransaction> findByContributionPeriod_ContributionDefinition_FundDestination(
            FundDestination fundDestination);

    List<ContributionTransaction> findByMemberIdAndContributionPeriod_ContributionDefinition_FundDestination(
            UUID memberId, FundDestination fundDestination);
}
