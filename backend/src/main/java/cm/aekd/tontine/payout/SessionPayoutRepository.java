package cm.aekd.tontine.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SessionPayoutRepository extends JpaRepository<SessionPayout, UUID> {

    List<SessionPayout> findBySessionIdOrderByPayoutDateAscCreatedAtAsc(UUID sessionId);

    boolean existsBySessionId(UUID sessionId);

    boolean existsBySessionIdAndMemberId(UUID sessionId, UUID memberId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SessionPayout p")
    BigDecimal sumAll();
}
