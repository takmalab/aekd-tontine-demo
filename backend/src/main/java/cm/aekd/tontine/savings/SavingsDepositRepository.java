package cm.aekd.tontine.savings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SavingsDepositRepository extends JpaRepository<SavingsDeposit, UUID> {

    List<SavingsDeposit> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    List<SavingsDeposit> findAllByOrderByCreatedAtDesc();

    List<SavingsDeposit> findByStatusOrderByCreatedAtAsc(SavingsDepositStatus status);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM SavingsDeposit d "
            + "WHERE d.status = cm.aekd.tontine.savings.SavingsDepositStatus.VALIDATED")
    BigDecimal sumValidated();

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM SavingsDeposit d "
            + "WHERE d.status = cm.aekd.tontine.savings.SavingsDepositStatus.VALIDATED AND d.member.id = :memberId")
    BigDecimal sumValidatedByMemberId(@Param("memberId") UUID memberId);
}
