package cm.aekd.tontine.fund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountOperationRepository extends JpaRepository<AccountOperation, UUID> {

    List<AccountOperation> findAllByOrderByCreatedAtDesc();

    /** Tout ce qui est sorti d'un compte : ses dépenses et ses virements émis. */
    @Query("select coalesce(sum(o.amount), 0) from AccountOperation o where o.sourceAccount = :account")
    BigDecimal sumOut(@Param("account") FundAccount account);

    @Query("select coalesce(sum(o.amount), 0) from AccountOperation o where o.sourceAccount = :account and o.type = :type")
    BigDecimal sumOutByType(@Param("account") FundAccount account, @Param("type") AccountOperationType type);

    /** Virements reçus par un compte. */
    @Query("select coalesce(sum(o.amount), 0) from AccountOperation o where o.targetAccount = :account")
    BigDecimal sumIn(@Param("account") FundAccount account);

    /**
     * Sérialise les opérations jusqu'à la fin de la transaction : deux dépenses
     * simultanées ne peuvent pas passer chacune le contrôle de solde.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext('account_operation'))) l", nativeQuery = true)
    Integer lockAccounts();
}
