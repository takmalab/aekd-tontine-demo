package cm.aekd.tontine.sanction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SanctionPaymentRepository extends JpaRepository<SanctionPayment, UUID> {

    List<SanctionPayment> findByAppliedSanctionIdOrderByCreatedAtAsc(UUID appliedSanctionId);

    List<SanctionPayment> findByStatusOrderByCreatedAtAsc(SanctionPaymentStatus status);

    List<SanctionPayment> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    boolean existsByAppliedSanctionIdAndStatusIn(UUID appliedSanctionId, List<SanctionPaymentStatus> statuses);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SanctionPayment p "
            + "WHERE p.appliedSanction.id = :sanctionId AND p.status = :status")
    BigDecimal sumBySanctionAndStatus(@Param("sanctionId") UUID sanctionId, @Param("status") SanctionPaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SanctionPayment p "
            + "WHERE p.status = cm.aekd.tontine.sanction.SanctionPaymentStatus.VALIDATED")
    BigDecimal sumValidated();
}
